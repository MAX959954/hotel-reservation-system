package com.folio.reviews;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByRoomId(Long roomId);

    List<Review> findByUserId(Long userId);

    // At most one: reviews.booking_id is UNIQUE (V4 migration).
    Optional<Review> findByBookingId(Long bookingId);

    List<Review> findByRoomIdAndApproved(Long roomId, boolean approved);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.room.id = :roomId AND r.approved = true")
    Double findAverageRatingByRoomId(@Param("roomId") Long roomId);
}
