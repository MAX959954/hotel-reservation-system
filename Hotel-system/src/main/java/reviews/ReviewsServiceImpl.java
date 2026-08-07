package reviews;

import booking.Booking;
import booking.BookingRepository;
import booking.BookingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewsServiceImpl implements ReviewsService {

    private final ReviewsRepository reviewsRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public ReviewsResponse create(ReviewsRequest request) {
        Booking booking = findBookingById(request.getBookingId());

        if (booking.getBookingStatus() != BookingStatus.COMPLETED) {
            throw new IllegalStateException("Reviews can only be sumbitted for COMPLETED bookings");
        }

        if (reviewsRepository.findByBookingId(booking.getId()).stream().findFirst().isPresent()){
            throw new IllegalStateException("A review already exists for that booking");
        }

        Reviews reviews = Reviews.builder()
                .booking(booking)
                .user(booking.getUser())
                .room(booking.getRoom())
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        return toResponse(reviewsRepository.save(reviews));
    }

    @Override
    public ReviewsResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public List<ReviewsResponse> getByRoom(Long roomId) {
        return reviewsRepository.findByRoomId(roomId).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<ReviewsResponse> getApprovedByRoom(Long roomId) {
        return reviewsRepository.findByRoomIdAndApproved(roomId, true).stream().map(this::toResponse).toList();
    }

    @Override
    public List<ReviewsResponse> getByUser (Long userId) {
        return reviewsRepository.findByUserId(userId).stream().map(this :: toResponse).toList();
    }

    @Override
    public ReviewsResponse getByBooking(Long bookingId) {
        return reviewsRepository.findByBookingId(bookingId).stream()
                .findFirst()
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalStateException("Review not found for booking id: " + bookingId));
    }

    @Override
    public Double getAverageRatingByRoom (Long roomId) {
        return reviewsRepository.findAverageRatingByRoomId(roomId);
    }


    @Override
    public ReviewsResponse approve(Long id) {
        Reviews reviews = findById(id);

        if (reviews.isApproved()) {
            throw new IllegalStateException("Review is already approved");
        }

        reviews.setApproved(true);
        return toResponse(reviewsRepository.save(reviews));
    }

    @Override
    public void delete(Long id) {
        reviewsRepository.delete(findById(id));
    }

    private Reviews findById(Long id) {
        return reviewsRepository.findById(id).orElseThrow(() -> new IllegalStateException("Review not found by id: " + id));
    }

    private Booking findBookingById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Booking not found by that id " + id));
    }

    private ReviewsResponse toResponse(Reviews reviews) {
        return ReviewsResponse.builder()
                .id(reviews.getId())
                .bookingId(reviews.getBooking().getId())
                .userId(reviews.getUser().getId())
                .userFullName(reviews.getUser().getFirstName() + " " + reviews.getUser().getLastName())
                .roomId(reviews.getRoom().getId())
                .roomNumber(reviews.getRoom().getNumber())
                .hotelName(reviews.getRoom().getHotel().getName())
                .rating(reviews.getRating())
                .comment(reviews.getComment())
                .approved(reviews.isApproved())
                .createdAt(reviews.getCreated_at())
                .stayCheckIn(reviews.getBooking().getCheck_in())
                .stayCheckOut(reviews.getBooking().getCheck_out())
                .build();
    }

}
