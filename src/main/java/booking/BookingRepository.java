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

    @Query("SELECT b FROM Booking b WHERE b.room.id = :roomId AND b.check_in < :checkOut AND b.check_out > :checkIn")
    List<Booking> findOverlappingBookings(@Param("roomId") Long roomId,
                                          @Param("checkIn") LocalDateTime checkIn,
                                          @Param("checkOut") LocalDateTime checkOut);

    List<Booking> findByUserIdAndBookingStatus(Long userId, BookingStatus status);
}
