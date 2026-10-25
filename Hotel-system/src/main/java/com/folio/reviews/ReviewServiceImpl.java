package com.folio.reviews;

import com.folio.exception.ResourceNotFoundException;
import com.folio.booking.Booking;
import com.folio.booking.BookingRepository;
import com.folio.booking.BookingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewsRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public ReviewResponse create(ReviewRequest request) {
        Booking booking = findBookingById(request.getBookingId());

        if (booking.getBookingStatus() != BookingStatus.COMPLETED) {
            throw new IllegalStateException("Reviews can only be submitted for COMPLETED bookings");
        }

        // Fast path with a clear message; the UNIQUE constraint on reviews.booking_id is
        // what actually guarantees one review per booking when two submissions race
        // (e.g. a double click) and both pass this check.
        if (reviewsRepository.findByBookingId(booking.getId()).isPresent()){
            throw new IllegalStateException("A review already exists for that booking");
        }

        Review reviews = Review.builder()
                .booking(booking)
                .user(booking.getUser())
                .room(booking.getRoom())
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        try {
            // Flushed here so a violation surfaces as this method's own error, not as a
            // generic failure at commit time.
            return toResponse(reviewsRepository.saveAndFlush(reviews));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("A review already exists for that booking");
        }
    }

    @Override
    public ReviewResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public List<ReviewResponse> getByRoom(Long roomId) {
        return reviewsRepository.findByRoomId(roomId).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<ReviewResponse> getApprovedByRoom(Long roomId) {
        return reviewsRepository.findByRoomIdAndApproved(roomId, true).stream().map(this::toResponse).toList();
    }

    @Override
    public List<ReviewResponse> getByUser (Long userId) {
        return reviewsRepository.findByUserId(userId).stream().map(this :: toResponse).toList();
    }

    @Override
    public ReviewResponse getByBooking(Long bookingId) {
        return reviewsRepository.findByBookingId(bookingId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found for booking id: " + bookingId));
    }

    @Override
    public Double getAverageRatingByRoom (Long roomId) {
        return reviewsRepository.findAverageRatingByRoomId(roomId);
    }


    @Override
    public ReviewResponse approve(Long id) {
        Review reviews = findById(id);

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

    private Review findById(Long id) {
        return reviewsRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Review not found by id: " + id));
    }

    private Booking findBookingById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found by that id " + id));
    }

    private ReviewResponse toResponse(Review reviews) {
        return ReviewResponse.builder()
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
                .createdAt(reviews.getCreatedAt())
                .stayCheckIn(reviews.getBooking().getCheckIn())
                .stayCheckOut(reviews.getBooking().getCheckOut())
                .build();
    }

}
