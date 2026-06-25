package reviews;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewsRepository extends JpaRepository<Reviews, Long> {

    List<Reviews> findByRoomId (Long roomId);

    // All reviews by a specific user
    List<Reviews> findByUserId(Long userId);

    // All reviews for a specific booking
    List<Reviews> findByBookingId(Long bookingId);

    // Only approved reviews for a room (visible to guests)
    List<Reviews> findByRoomIdAndIs_approved(Long roomId, boolean isApproved);

    // Average rating for a room
    @Query("SELECT AVG(r.rating) FROM Reviews r WHERE r.room.id = :roomId AND r.is_approved = true")
    Double findAverageRatingByRoomId(@Param("roomId") Long roomId);

}
