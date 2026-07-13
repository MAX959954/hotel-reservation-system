package booking;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import room.Room;
import room.RoomRepository;
import room.RoomStatus;
import user.User;
import user.UserRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService{

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public BookingResponse create(BookingRequest request){
        if (!request.getCheckOut().isAfter(request.getCheckIn())){
            throw new IllegalStateException("Check-out must be after check-in");
        }

        Room room = findByRoomId(request.getRoomId());
        User user = findByUserId (request.getUserId());

        if (room.getStatus() != RoomStatus.AVAILABLE){
            throw new IllegalStateException("Room id not available");
        }

        boolean hasOverlap = !bookingRepository.findOverlappingBookings(
                room.getId() , request.getCheckIn() , request.getCheckOut()).isEmpty();

        if (hasOverlap) {
            throw new IllegalStateException("Room is already booked for the selected dates");
        }

        if(request.getGuestCount() > room.getCapacity()) {
            throw new IllegalStateException("Guest count exceeds room capacity of " + room.getCapacity());
        }

        long nights = ChronoUnit.DAYS.between(request.getCheckIn() , request.getCheckOut());
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
    @Transactional
    public BookingResponse confirm(Long id) {
        Booking booking = findById(id);

        if(booking.getBookingStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Only PENDING bookings can be confirmed");
        }

        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setConfirmed_at(LocalDateTime.now());
        booking.getRoom().setStatus(RoomStatus.RESERVED);
        return  toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponse cancel(Long id) {
        Booking booking = findById(id);

        if(booking.getBookingStatus() == BookingStatus.COMPLETED ||
        booking.getBookingStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel a " + booking.getBookingStatus() + "booking");
        }
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancelled_at(LocalDateTime.now());
        booking.getRoom().setStatus(RoomStatus.AVAILABLE);
        return toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponse checkIn(Long id) {
        Booking booking = findById(id);

        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only CONFIRMED bookings can be checked in");
        }
        booking.setBookingStatus(BookingStatus.CHECKED_IN);
        booking.getRoom().setStatus(RoomStatus.OCCUPIED);
        return toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponse complete(Long id) {
        Booking booking = findById(id);

        if(booking.getBookingStatus() != BookingStatus.CHECKED_IN) {
            throw new IllegalStateException("Only CHECKED_IN bookings can be completed");
        }
        booking.setBookingStatus(BookingStatus.COMPLETED);
        booking.getRoom().setStatus(RoomStatus.AVAILABLE);
        return toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        bookingRepository.delete(findById(id));
    }

    public Booking findById(Long id) {
        return bookingRepository.findById(id).orElseThrow(() -> new IllegalStateException("Booking not found by id: " + id));
    }

    private Room findByRoomId (Long roomId) {
        return roomRepository.findByIdForUpdate(roomId).orElseThrow(() -> new IllegalStateException("Room not found by that id " + roomId));
    }

    private User findByUserId (Long userId ) {
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("User not found by that email"+ userId));
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
