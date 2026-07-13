package reviews;

import java.util.List;

public interface ReviewsService {

    ReviewsResponse create(ReviewsRequest request);

    ReviewsResponse getById(Long id);

    List<ReviewsResponse> getByRoom(Long roomId);

    List<ReviewsResponse> getApprovedByRoom(Long roomId);

    List<ReviewsResponse> getByUser(Long userId);

    ReviewsResponse getByBooking(Long bookingId);

    Double getAverageRatingByRoom(Long roomId)  ;

    ReviewsResponse approve(Long id);

    void delete(Long id);
}
