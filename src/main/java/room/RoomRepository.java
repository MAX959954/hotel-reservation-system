package room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByHotelId(Long hotelId);

    List<Room> findByType(RoomType type) ;

    List<Room> findByStatus(RoomStatus status);


    List<Room> findByHotelIdAndStatus( Long hotelId, RoomStatus status);

    List<Room> findByHotelIdAndTypeAndStatus( Long hotelId, RoomType roomType ,  RoomStatus status);

    List<Room> findByHotelIdAndCapacityGreaterThanEqual( Long hotelId, Integer guestCount);

    @Query("SELECT r FROM Room r WHERE r.hotel.id = :hotelId AND r.id NOT IN " +
            "(SELECT b.room.id FROM Booking b WHERE b.check_in < :checkOut AND b.check_out > :checkIn)")
    List<Room> findAvailableRooms(@Param("hotelId") Long hotelId,
                                  @Param("checkIn") LocalDateTime checkIn,
                                  @Param("checkOut") LocalDateTime checkOut);
}
