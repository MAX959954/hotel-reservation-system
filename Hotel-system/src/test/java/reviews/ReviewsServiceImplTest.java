package reviews;
import booking.Booking;
import booking.BookingRepository;
import booking.BookingStatus;
import hotels.Hotels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import room.Room;
import user.User;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)

public class ReviewsServiceImplTest {

    @Mock
    private ReviewsRepository reviewsRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private ReviewsServiceImpl reviewsService;

    private User user;
    private Room room;
    private Booking booking;
    private Reviews review;
    private ReviewsRequest request;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .firstName("Jane")
                .lastName("Doe")
                .build();

        Hotels hotel = Hotels.builder()
                .id(1L)
                .name("Grand Hotel")
                .build();

        room = Room.builder()
                .id(1L)
                .number("101")
                .hotel(hotel)
                .build();

        booking = Booking.builder()
                .id(1L)
                .user(user)
                .room(room)
                .bookingStatus(BookingStatus.COMPLETED)
                .build();

        review = Reviews.builder()
                .id(1L)
                .booking(booking)
                .user(user)
                .room(room)
                .rating(5)
                .comment("Great stay")
                .approved(false)
                .build();

        request = new ReviewsRequest();
        request.setBookingId(1L);
        request.setRating(5);
        request.setComment("Great stay");
    }

    @Test
    void create_savesReviewAndReturnsResponse_whenBookingCompletedAndNoExistingReview() {
        given(bookingRepository.findById(1L)).willReturn(Optional.of(booking));
        given(reviewsRepository.findByBookingId(1L)).willReturn(List.of());
        given(reviewsRepository.save(any(Reviews.class))).willReturn(review);

        ReviewsResponse response = reviewsService.create(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getBookingId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getUserFullName()).isEqualTo("Jane Doe");
        assertThat(response.getRoomId()).isEqualTo(1L);
        assertThat(response.getRoomNumber()).isEqualTo("101");
        assertThat(response.getHotelName()).isEqualTo("Grand Hotel");
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.isApproved()).isFalse();

        ArgumentCaptor<Reviews> captor = ArgumentCaptor.forClass(Reviews.class);
        verify(reviewsRepository).save(captor.capture());
        Reviews saved = captor.getValue();

        assertThat(saved.getBooking()).isEqualTo(booking);
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getRoom()).isEqualTo(room);
        assertThat(saved.getComment()).isEqualTo("Great stay");
    }

    @Test
    void create_throws_whenBookingNotFound() {
        given(bookingRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewsService.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Booking not found by that id");
        verify(reviewsRepository , never()).save(any());
    }

    @Test
    void create_throws_whenBookingNotCompleted(){
        booking.setBookingStatus(BookingStatus.CONFIRMED);

        given(bookingRepository.findById(1L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> reviewsService.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reviews can only be sumbitted for COMPLETED bookings");
        verify(reviewsRepository , never()).save(any());
    }

    @Test
    void getById_returnsReview_whenFound() {
        given(reviewsRepository.findById(1L)).willReturn(Optional.of(review));

        ReviewsResponse response = reviewsService.getById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getRating()).isEqualTo(5);
    }

    @Test
    void getById_throws_whenNotFound() {
        given(reviewsRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewsService.getById(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Review not found by id");
    }

    @Test
    void getByRoom_returnsReviews() {
        given(reviewsRepository.findByRoomId(1L)).willReturn(List.of(review));

        List<ReviewsResponse> responses = reviewsService.getByRoom(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getRoomId()).isEqualTo(1L);
    }

    @Test
    void getApprovedByRoom_returnsOnlyApprovedReviews() {
        review.setApproved(true);
        given(reviewsRepository.findByRoomIdAndApproved(1L, true)).willReturn(List.of(review));

        List<ReviewsResponse> responses = reviewsService.getApprovedByRoom(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).isApproved()).isTrue();
    }

    @Test
    void getByUser_returnsReviews() {
        given(reviewsRepository.findByUserId(1L)).willReturn(List.of(review));

        List<ReviewsResponse> responses = reviewsService.getByUser(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getUserId()).isEqualTo(1L);
    }

    @Test
    void getByBooking_returnsReview_whenFound() {
        given(reviewsRepository.findByBookingId(1L)).willReturn(List.of(review));

        ReviewsResponse response = reviewsService.getByBooking(1L);

        assertThat(response.getBookingId()).isEqualTo(1L);
    }

    @Test
    void getByBooking_throws_whenNotFound() {
        given(reviewsRepository.findByBookingId(1L)).willReturn(List.of());

        assertThatThrownBy(() -> reviewsService.getByBooking(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Review not found for booking id");
    }

    // ---------- getAverageRatingByRoom ----------

    @Test
    void getAverageRatingByRoom_returnsAverage() {
        given(reviewsRepository.findAverageRatingByRoomId(1L)).willReturn(4.5);

        Double average = reviewsService.getAverageRatingByRoom(1L);

        assertThat(average).isEqualTo(4.5);
    }

    // ---------- approve ----------

    @Test
    void approve_setsApprovedAndReturnsResponse_whenNotAlreadyApproved() {
        given(reviewsRepository.findById(1L)).willReturn(Optional.of(review));
        given(reviewsRepository.save(any(Reviews.class))).willReturn(review);

        ReviewsResponse response = reviewsService.approve(1L);

        assertThat(response.isApproved()).isTrue();

        ArgumentCaptor<Reviews> captor = ArgumentCaptor.forClass(Reviews.class);
        verify(reviewsRepository).save(captor.capture());
        assertThat(captor.getValue().isApproved()).isTrue();
    }

    @Test
    void approve_throws_whenAlreadyApproved() {
        review.setApproved(true);
        given(reviewsRepository.findById(1L)).willReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewsService.approve(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Review is already approved");

        verify(reviewsRepository, never()).save(any());
    }

    @Test
    void approve_throws_whenReviewNotFound() {
        given(reviewsRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewsService.approve(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Review not found by id");

        verify(reviewsRepository, never()).save(any());
    }

    // ---------- delete ----------

    @Test
    void delete_deletesReview_whenFound() {
        given(reviewsRepository.findById(1L)).willReturn(Optional.of(review));

        reviewsService.delete(1L);

        verify(reviewsRepository).delete(review);
    }

    @Test
    void delete_throws_whenReviewNotFound() {
        given(reviewsRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewsService.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Review not found by id");
    }


}
