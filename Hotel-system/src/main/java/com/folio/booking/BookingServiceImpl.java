package com.folio.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.folio.companies.CompanyStatus;
import com.folio.exception.ConflictException;
import com.folio.hotels.HotelStatus;
import com.folio.hotels.Hotel;
import com.folio.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.folio.payment.PaymentRepository;
import com.folio.payment.PaymentService;
import com.folio.payment.PaymentStatus;
import com.folio.reviews.ReviewRepository;
import com.folio.room.Room;
import com.folio.room.RoomRepository;
import com.folio.room.RoomStatus;
import com.folio.user.MailService;
import com.folio.user.User;
import com.folio.user.UserRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService{

    private static final Set<PaymentStatus> PAID_STATUSES =
            EnumSet.of(PaymentStatus.COMPLETED, PaymentStatus.PARTIALLY_REFUNDED);

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final ReviewRepository reviewsRepository;

    // Cancellation policy — see app.bookings in application.yml. Plain field injection,
    // same convention as PaymentServiceImpl.webhookSecret.
    @Value("${app.bookings.free-cancellation-hours:48}")
    private long freeCancellationHours;

    @Value("${app.bookings.late-cancellation-fee-nights:1}")
    private long lateCancellationFeeNights;

    @Override
    @Transactional
    @CacheEvict(cacheNames = "roomsAvailability" , allEntries = true)
    public BookingResponse create(BookingRequest request){
        if (!request.getCheckOut().isAfter(request.getCheckIn())){
            throw new IllegalStateException("Check-out must be after check-in");
        }
        // Price is nights (calendar dates, see below) * rate, so a same-day stay
        // (e.g. 10:00 -> 18:00 on one date) passed the check above but priced at 0 —
        // a free booking that still blocked the room.
        if (!request.getCheckOut().toLocalDate().isAfter(request.getCheckIn().toLocalDate())) {
            throw new IllegalStateException("A booking must be at least one night");
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

        // Only an ACTIVE hotel of an ACTIVE (approved, not suspended) company takes new
        // bookings. UNDER_RENOVATION/COMINGS_SOON hotels stay visible but aren't bookable.
        Hotel hotel = room.getHotel();
        if (hotel.getStatus() != HotelStatus.ACTIVE || hotel.getCompany().getStatus() != CompanyStatus.ACTIVE) {
            throw new IllegalStateException("This hotel is not accepting bookings");
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
        // BigDecimal, not double: exact cents (price is stored as numeric(12,2)).
        BigDecimal totalPrice = room.getPricePerNight()
                .multiply(BigDecimal.valueOf(nights))
                .setScale(2, RoundingMode.HALF_UP);

        Booking booking = Booking.builder()
                .user(user)
                .room(room)
                .checkIn(request.getCheckIn())
                .checkOut(request.getCheckOut())
                .guestCount(request.getGuestCount())
                .totalPrice(totalPrice)
                .specialRequest(request.getSpecialRequest())
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
        booking.setConfirmedAt(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);
        publishStatusChange(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames =  "roomsAvailability" , allEntries = true)
    public BookingResponse cancel(Long id) {
        // Hotel/admin/system cancellation: the guest isn't at fault, so nothing is kept.
        return cancelKeepingFee(findById(id), BigDecimal.ZERO);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames =  "roomsAvailability" , allEntries = true)
    public BookingResponse cancelByGuest(Long id) {
        Booking booking = findById(id);
        // Only a CONFIRMED booking can have been paid (PaymentServiceImpl.assertPayable),
        // so a fee is only ever relevant there.
        boolean late = booking.getBookingStatus() == BookingStatus.CONFIRMED
                && LocalDateTime.now().isAfter(freeCancellationUntil(booking));
        return cancelKeepingFee(booking, late ? lateCancellationFee(booking) : BigDecimal.ZERO);
    }

    // Free cancellation ends this many hours before check-in.
    private LocalDateTime freeCancellationUntil(Booking booking) {
        return booking.getCheckIn().minusHours(freeCancellationHours);
    }

    // The price of the first N nights, at the nightly rate the guest actually booked
    // (total / nights — unaffected by later price changes), never more than the total.
    private BigDecimal lateCancellationFee(Booking booking) {
        long nights = ChronoUnit.DAYS.between(booking.getCheckIn().toLocalDate(), booking.getCheckOut().toLocalDate());
        if (nights <= 0) {
            return booking.getTotalPrice();
        }
        BigDecimal perNight = booking.getTotalPrice().divide(BigDecimal.valueOf(nights), 2, RoundingMode.HALF_UP);
        return perNight.multiply(BigDecimal.valueOf(Math.min(lateCancellationFeeNights, nights)))
                .min(booking.getTotalPrice());
    }

    private BookingResponse cancelKeepingFee(Booking booking, BigDecimal retainedFee) {
        // Cancelling is only meaningful before the stay starts. It used to be allowed from
        // any non-terminal status, so a guest could cancel a stay they were already
        // checked in to (freeing the room's dates while they occupied it) or retro-cancel
        // a NO_SHOW. Once CHECKED_IN, the only way forward is complete().
        BookingStatus current = booking.getBookingStatus();
        if (current != BookingStatus.PENDING && current != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel a " + current + " booking");
        }
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);
        // Refunds a paid booking / cancels a payment still in progress. Same transaction:
        // if the refund fails, the cancellation rolls back too, so the guest never ends up
        // with a cancelled stay and no money back. The Stripe call itself is external and
        // can't be rolled back, so it runs only after this cancellation is flushed (see
        // PaymentServiceImpl.refundPayment); a retry after a failed commit is idempotent.
        paymentService.settleForCancelledBooking(saved.getId(), retainedFee);
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
        // The stay is paid before it starts. A guest paying cash on arrival hands it over
        // at the desk, and staff mark it received (PaymentService.markReceived) first.
        boolean paid = paymentRepository.findByBookingId(id)
                .map(payment -> PAID_STATUSES.contains(payment.getStatus()))
                .orElse(false);
        if (!paid) {
            throw new IllegalStateException("The booking must be paid before check-in");
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
        // A no-show keeps whatever was paid (the fee is the whole price, so nothing is
        // refunded), but a payment still PENDING — cash the guest never brought, a card
        // attempt never finished — is closed instead of staying open forever.
        paymentService.settleForCancelledBooking(saved.getId(), saved.getTotalPrice());
        publishStatusChange(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames =  "roomsAvailability" , allEntries = true)
    public int cancelUpcomingForCompany(Long companyId) {
        return cancelAll(bookingRepository.findUpcomingByCompanyId(companyId));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames =  "roomsAvailability" , allEntries = true)
    public int cancelUpcomingForHotel(Long hotelId) {
        return cancelAll(bookingRepository.findUpcomingByHotelId(hotelId));
    }

    // Hotel-side cancellation (full refund) of every booking in the list, in the caller's
    // transaction: if one refund fails, nothing is cancelled and the status change that
    // triggered this is rejected too, rather than leaving some guests without their money.
    private int cancelAll(List<Booking> bookings) {
        bookings.forEach(booking -> cancelKeepingFee(booking, BigDecimal.ZERO));
        return bookings.size();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Booking booking = findById(id);
        // payments and reviews reference bookings by foreign key, so deleting such a
        // booking used to fail in the database and come back as a 500. Payment history is
        // a financial record and must be kept anyway — such bookings are cancelled, not
        // deleted.
        if (paymentRepository.findByBookingId(id).isPresent()) {
            throw new ConflictException("Booking " + id + " has a payment record and cannot be deleted; cancel it instead");
        }
        if (!reviewsRepository.findByBookingId(id).isEmpty()) {
            throw new ConflictException("Booking " + id + " has a review and cannot be deleted; cancel it instead");
        }
        bookingRepository.delete(booking);
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
                booking.getCheckIn(),
                booking.getCheckOut(),
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
        return bookingRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found by id: " + id));
    }

    private Room findByRoomId (Long roomId) {
        return roomRepository.findByIdForUpdate(roomId).orElseThrow(() -> new ResourceNotFoundException("Room not found by that id " + roomId));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found by that email: " + email));
    }

    private BookingResponse toResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .userId(booking.getUser().getId())
                .userFullName(booking.getUser().getFirstName() + " " + booking.getUser().getLastName())
                .roomId(booking.getRoom().getId())
                .roomNumber(booking.getRoom().getNumber())
                .hotelId(booking.getRoom().getHotel().getId())
                .hotelName(booking.getRoom().getHotel().getName())
                .checkIn(booking.getCheckIn())
                .checkOut(booking.getCheckOut())
                .guestCount(booking.getGuestCount())
                .bookingStatus(booking.getBookingStatus())
                .totalPrice(booking.getTotalPrice())
                .specialRequest(booking.getSpecialRequest())
                .confirmedAt(booking.getConfirmedAt())
                .cancelledAt(booking.getCancelledAt())
                .freeCancellationUntil(freeCancellationUntil(booking))
                .lateCancellationFee(lateCancellationFee(booking))
                .createdAt(booking.getCreatedAt())
                .build();
    }

}
