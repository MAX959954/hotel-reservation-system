package booking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import room.Room;
import room.RoomRepository;
import room.RoomStatus;
import user.MailService;
import user.User;
import user.UserRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService{

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    @CacheEvict(cacheNames = "roomsAvailability" , allEntries = true)
    public BookingResponse create(BookingRequest request){
        if (!request.getCheckOut().isAfter(request.getCheckIn())){
            throw new IllegalStateException("Check-out must be after check-in");
        }

        Room room = findByRoomId(request.getRoomId());
        // The booking always belongs to whoever the JWT says is calling — never to a
        // client-supplied ID, which would let any authenticated user book on someone
        // else's account by editing the request body.
        User user = currentUser();

        // Only an operational flag (staff-set, independent of any booking) blocks
        // creation outright. Whether the room is free for these specific dates is
        // decided solely by the overlap check below — RoomStatus is not a per-date
        // calendar, so it must never gate booking on its own (that previously made a
        // room permanently unbookable for ANY future dates as soon as one stay on it
        // was confirmed).
        if (room.getStatus() == RoomStatus.MAINTENANCE || room.getStatus() == RoomStatus.OUT_OF_ORDER){
            throw new IllegalStateException("Room is not available for booking");
        }

        boolean hasOverlap = !bookingRepository.findOverlappingBookings(
                room.getId() , request.getCheckIn() , request.getCheckOut()).isEmpty();

        if (hasOverlap) {
            throw new IllegalStateException("Room is already booked for the selected dates");
        }

        if(request.getGuestCount() > room.getCapacity()) {
            throw new IllegalStateException("Guest count exceeds room capacity of " + room.getCapacity());
        }

        // Nights is a calendar-date span (industry-standard "nights stayed"), not raw
        // elapsed hours — check-in/check-out happen at different times of day (e.g. 3pm/
        // 11am), so ChronoUnit.DAYS.between on the full timestamps would floor a 3-night
        // stay (Mon 3pm -> Thu 11am, 68 hours) down to 2.
        long nights = ChronoUnit.DAYS.between(request.getCheckIn().toLocalDate() , request.getCheckOut().toLocalDate());
        double totalPrirce = nights * room.getPrice_per_night();

        Booking booking = Booking.builder()
                .user(user)
                .room(room)
                .check_in(request.getCheckIn())
                .check_out(request.getCheckOut())
                .guestCount(request.getGuestCount())
                .totalPrice(totalPrirce)
                .special_request(request.getSpecialRequest())
                .build();

        return toResponse(bookingRepository.save(booking));

    }

    @Override
    public BookingResponse getById(Long id){
        return toResponse(findById(id));
    }

    @Override
    public List<BookingResponse> getByUserId(Long userId){
        return bookingRepository.findByUserId(userId).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<BookingResponse> getByRoom(Long roomId) {
        return bookingRepository.findByRoomId(roomId).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<BookingResponse> getByStatus(BookingStatus status) {
        return bookingRepository.findByBookingStatus(status).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<BookingResponse> getByUserAndStatus(Long userId , BookingStatus status) {
        return bookingRepository.findByUserIdAndBookingStatus(userId , status).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<BookingResponse> getByCompany(Long companyId , BookingStatus status) {
        List<Booking> bookings = status == null
                ? bookingRepository.findByRoom_Hotel_Company_Id(companyId)
                : bookingRepository.findByRoom_Hotel_Company_IdAndBookingStatus(companyId , status);
        return bookings.stream().map(this :: toResponse).toList();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames =  "roomsAvailability" ,allEntries = true)
    public BookingResponse confirm(Long id) {
        Booking booking = findById(id);

        if(booking.getBookingStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Only PENDING bookings can be confirmed");
        }

        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setConfirmed_at(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);
        publishStatusChange(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames =  "roomsAvailability" , allEntries = true)
    public BookingResponse cancel(Long id) {
        Booking booking = findById(id);

        if(booking.getBookingStatus() == BookingStatus.COMPLETED ||
        booking.getBookingStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel a " + booking.getBookingStatus() + "booking");
        }
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancelled_at(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);
        publishStatusChange(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames =  "roomsAvailability" , allEntries = true)
    public BookingResponse checkIn(Long id) {
        Booking booking = findById(id);

        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only CONFIRMED bookings can be checked in");
        }
        booking.setBookingStatus(BookingStatus.CHECKED_IN);
        Booking saved = bookingRepository.save(booking);
        publishStatusChange(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames =  "roomsAvailability" , allEntries = true)
    public BookingResponse complete(Long id) {
        Booking booking = findById(id);

        if(booking.getBookingStatus() != BookingStatus.CHECKED_IN) {
            throw new IllegalStateException("Only CHECKED_IN bookings can be completed");
        }
        booking.setBookingStatus(BookingStatus.COMPLETED);
        Booking saved = bookingRepository.save(booking);
        publishStatusChange(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames =  "roomsAvailability" , allEntries = true)
    public BookingResponse noShow(Long id) {
        Booking booking = findById(id);

        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only CONFIRMED bookings can be marked as no-show");
        }

        booking.setBookingStatus(BookingStatus.NO_SHOW);
        Booking saved = bookingRepository.save(booking);
        publishStatusChange(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        bookingRepository.delete(findById(id));
    }

    // Published now but only delivered after the transaction commits (see
    // sendStatusEmail below) — same reasoning as PaymentServiceImpl.publishConfirmation:
    // the booking row must not depend on mail delivery succeeding.
    private void publishStatusChange(Booking booking) {
        eventPublisher.publishEvent(new BookingStatusChangedEvent(
                booking.getUser().getEmail(),
                booking.getUser().getFirstName(),
                booking.getRoom().getHotel().getName(),
                booking.getRoom().getNumber(),
                booking.getCheck_in(),
                booking.getCheck_out(),
                booking.getBookingStatus()
        ));
    }

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendStatusEmail(BookingStatusChangedEvent event) {
        try {
            switch (event.status()) {
                case CONFIRMED -> mailService.sendBookingConfirmed(
                        event.email(), event.guestName(), event.hotelName(), event.roomNumber(),
                        event.checkIn(), event.checkOut());
                case CANCELLED -> mailService.sendBookingCancelled(
                        event.email(), event.guestName(), event.hotelName(), event.roomNumber(),
                        event.checkIn(), event.checkOut());
                case CHECKED_IN -> mailService.sendBookingCheckedIn(
                        event.email(), event.guestName(), event.hotelName(), event.roomNumber(),
                        event.checkIn(), event.checkOut());
                case COMPLETED -> mailService.sendBookingCompleted(
                        event.email(), event.guestName(), event.hotelName(), event.roomNumber(),
                        event.checkIn(), event.checkOut());
                case NO_SHOW -> mailService.sendBookingNoShow(
                        event.email(), event.guestName(), event.hotelName(), event.roomNumber(),
                        event.checkIn(), event.checkOut());
                default -> log.debug("No email template for booking status {}", event.status());
            }
        } catch (Exception e) {
            log.warn("Could not deliver booking status email to {}: {}", event.email(), e.getMessage());
        }
    }

    public Booking findById(Long id) {
        return bookingRepository.findById(id).orElseThrow(() -> new IllegalStateException("Booking not found by id: " + id));
    }

    private Room findByRoomId (Long roomId) {
        return roomRepository.findByIdForUpdate(roomId).orElseThrow(() -> new IllegalStateException("Room not found by that id " + roomId));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found by that email: " + email));
    }

    private BookingResponse toResponse(Booking booking) {
        return BookingResponse.builder()
                .Id(booking.getId())
                .userId(booking.getUser().getId())
                .userFullName(booking.getUser().getFirstName() + " " + booking.getUser().getLastName())
                .roomId(booking.getRoom().getId())
                .roomNumber(booking.getRoom().getNumber())
                .hotelId(booking.getRoom().getHotel().getId())
                .hotelName(booking.getRoom().getHotel().getName())
                .checkIn(booking.getCheck_in())
                .checkOut(booking.getCheck_out())
                .guestCount(booking.getGuestCount())
                .bookingStatus(booking.getBookingStatus())
                .totalPrice(booking.getTotalPrice())
                .specialRequest(booking.getSpecial_request())
                .confirmedAt(booking.getConfirmed_at())
                .cancelledAt(booking.getCancelled_at())
                .createdAt(booking.getCreated_at())
                .build();
    }

}
