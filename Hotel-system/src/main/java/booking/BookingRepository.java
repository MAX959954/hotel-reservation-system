package booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    List<Booking> findByRoomId(Long roomId);

    List<Booking> findByBookingStatus(BookingStatus status);

    // CANCELLED/PAYMENT_FAILED bookings never occupied the room, so they must not keep
    // blocking those dates for everyone else.
    @Query("SELECT b FROM Booking b WHERE b.room.id = :roomId AND b.check_in < :checkOut AND b.check_out > :checkIn " +
            "AND b.bookingStatus NOT IN (booking.BookingStatus.CANCELLED, booking.BookingStatus.PAYMENT_FAILED)")
    List<Booking> findOverlappingBookings(@Param("roomId") Long roomId,
                                          @Param("checkIn") LocalDateTime checkIn,
                                          @Param("checkOut") LocalDateTime checkOut);

    List<Booking> findByUserIdAndBookingStatus(Long userId, BookingStatus status);

    List<Booking> findByRoom_Hotel_Company_Id(Long companyId);

    List<Booking> findByRoom_Hotel_Company_IdAndBookingStatus(Long companyId, BookingStatus status);
}
