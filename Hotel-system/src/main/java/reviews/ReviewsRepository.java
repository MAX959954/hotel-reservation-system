package reviews;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewsRepository extends JpaRepository<Reviews, Long> {

    List<Reviews> findByRoomId(Long roomId);

    List<Reviews> findByUserId(Long userId);

    List<Reviews> findByBookingId(Long bookingId);

    List<Reviews> findByRoomIdAndApproved(Long roomId, boolean approved);

    @Query("SELECT AVG(r.rating) FROM Reviews r WHERE r.room.id = :roomId AND r.approved = true")
    Double findAverageRatingByRoomId(@Param("roomId") Long roomId);
}
