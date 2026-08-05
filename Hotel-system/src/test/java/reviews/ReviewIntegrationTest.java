package reviews;

import booking.Booking;
import booking.BookingRepository;
import booking.BookingStatus;
import companies.Companies;
import companies.CompaniesRepository;
import hotels.Hotels;
import hotels.HotelsRepository;
import org.example.hotelsystem.HotelSystemApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import room.Room;
import room.RoomRepository;
import room.RoomStatus;
import room.RoomType;
import user.Roles;
import user.User;
import user.UserRepository;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Crosses review/booking/room/user repositories against a real (H2) database,
// exercising the "only one review per completed booking" rule end to end
// rather than through stubbed repository responses.
@SpringBootTest(classes = HotelSystemApplication.class)
@ActiveProfiles("test")
@Transactional
class ReviewIntegrationTest {

    @Autowired
    private ReviewsService reviewsService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private HotelsRepository hotelsRepository;

    @Autowired
    private CompaniesRepository companiesRepository;

    @Autowired
    private UserRepository userRepository;

    private Booking completedBooking;

    private Booking bookingWithStatus(BookingStatus status) {
        Companies company = companiesRepository.save(Companies.builder()
                .name("Acme Hospitality")
                .legal_name("Acme Hospitality LLC")
                .email("contact@acme.com")
                .phone("+123456789")
                .address("1 Main St")
                .city("Paris")
                .country("France")
                .website("https://acme.com")
                .build());

        Hotels hotel = hotelsRepository.save(Hotels.builder()
                .name("Grand Hotel")
                .city("Paris")
                .country("France")
                .address("1 Rue de Rivoli")
                .star_rating(4)
                .phone("+123456789")
                .email("contact@grandhotel.com")
                .description("A lovely hotel")
                .image_url("http://example.com/image.jpg")
                .company(company)
                .build());

        Room room = roomRepository.save(Room.builder()
                .number("101")
                .type(RoomType.DOUBLE)
                .price_per_night(100.0)
                .capacity(2)
                .floor(1)
                .status(RoomStatus.AVAILABLE)
                .hotel(hotel)
                .build());

        User guest = userRepository.save(User.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe+" + System.nanoTime() + "@example.com")
                .passwordHash("hashed")
                .phone("+1" + System.nanoTime() % 100000000L)
                .roles(Set.of(Roles.GUEST))
                .emailVerified(true)
                .enabled(true)
                .build());

        return bookingRepository.save(Booking.builder()
                .user(guest)
                .room(room)
                .check_in(java.time.LocalDateTime.now().minusDays(5))
                .check_out(java.time.LocalDateTime.now().minusDays(2))
                .guestCount(2)
                .bookingStatus(status)
                .totalPrice(300.0)
                .build());
    }

    @BeforeEach
    void setUp() {
        completedBooking = bookingWithStatus(BookingStatus.COMPLETED);
    }

    private ReviewsRequest requestFor(Long bookingId) {
        ReviewsRequest request = new ReviewsRequest();
        request.setBookingId(bookingId);
        request.setRating(5);
        request.setComment("Great stay");
        return request;
    }

    @Test
    void create_succeeds_forCompletedBookingWithNoExistingReview() {
        ReviewsResponse response = reviewsService.create(requestFor(completedBooking.getId()));

        assertThat(response.getBookingId()).isEqualTo(completedBooking.getId());
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.isApproved()).isFalse();
    }

    @Test
    void create_throws_whenBookingNotCompleted() {
        Booking pendingBooking = bookingWithStatus(BookingStatus.PENDING);

        assertThatThrownBy(() -> reviewsService.create(requestFor(pendingBooking.getId())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reviews can only be sumbitted for COMPLETED bookings");
    }

    @Test
    void create_throws_whenReviewAlreadyExistsForBooking() {
        reviewsService.create(requestFor(completedBooking.getId()));

        assertThatThrownBy(() -> reviewsService.create(requestFor(completedBooking.getId())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A review already exists for that booking");
    }
}
