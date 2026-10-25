package com.folio.reviews;

import java.util.List;

public interface ReviewService {

    ReviewResponse create(ReviewRequest request);

    ReviewResponse getById(Long id);

    List<ReviewResponse> getByRoom(Long roomId);

    List<ReviewResponse> getApprovedByRoom(Long roomId);

    List<ReviewResponse> getByUser(Long userId);

    ReviewResponse getByBooking(Long bookingId);

    Double getAverageRatingByRoom(Long roomId)  ;

    ReviewResponse approve(Long id);

    void delete(Long id);
}
