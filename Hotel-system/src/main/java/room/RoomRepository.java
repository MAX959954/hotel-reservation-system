package room;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :roomId")
    Optional<Room> findByIdForUpdate(@Param("roomId") Long roomId);

    List<Room> findByHotelId(Long hotelId);

    List<Room> findByType(RoomType type) ;

    List<Room> findByStatus(RoomStatus status);


    List<Room> findByHotelIdAndStatus( Long hotelId, RoomStatus status);

    List<Room> findByHotelIdAndTypeAndStatus( Long hotelId, RoomType roomType ,  RoomStatus status);

    List<Room> findByHotelIdAndCapacityGreaterThanEqual( Long hotelId, Integer guestCount);

    // Excludes rooms that are out of service (independent of any booking) and bookings
    // that never actually held the room (CANCELLED/PAYMENT_FAILED), so a cancelled stay
    // or a room taken out of maintenance frees those dates back up for search.
    @Query("SELECT r FROM Room r WHERE r.hotel.id = :hotelId " +
            "AND (:guestCount IS NULL OR r.capacity >= :guestCount) " +
            "AND r.status NOT IN (room.RoomStatus.MAINTENANCE, room.RoomStatus.OUT_OF_ORDER) " +
            "AND r.id NOT IN " +
            "(SELECT b.room.id FROM Booking b WHERE b.check_in < :checkOut AND b.check_out > :checkIn " +
            "AND b.bookingStatus NOT IN (booking.BookingStatus.CANCELLED, booking.BookingStatus.PAYMENT_FAILED))")
    List<Room> findAvailableRooms(@Param("hotelId") Long hotelId,
                                  @Param("checkIn") LocalDateTime checkIn,
                                  @Param("checkOut") LocalDateTime checkOut,
                                  @Param("guestCount") Integer guestCount);
}
