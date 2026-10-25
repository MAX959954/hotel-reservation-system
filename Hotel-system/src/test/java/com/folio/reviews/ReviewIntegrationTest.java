package com.folio.reviews;

import java.math.BigDecimal;
import com.folio.booking.Booking;
import com.folio.booking.BookingRepository;
import com.folio.booking.BookingStatus;
import com.folio.companies.Company;
import com.folio.companies.CompanyRepository;
import com.folio.companies.CompanyStatus;
import com.folio.hotels.Hotel;
import com.folio.hotels.HotelRepository;
import com.folio.HotelSystemApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.folio.room.Room;
import com.folio.room.RoomRepository;
import com.folio.room.RoomStatus;
import com.folio.room.RoomType;
import com.folio.user.Role;
import com.folio.user.User;
import com.folio.user.UserRepository;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Crosses review/booking/room/user repositories against a real PostgreSQL database (Testcontainers),
// exercising the "only one review per completed booking" rule end to end
// rather than through stubbed repository responses.
@SpringBootTest(classes = HotelSystemApplication.class)
@ActiveProfiles("test")
@Transactional
class ReviewIntegrationTest {

    @Autowired
    private ReviewService reviewsService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private HotelRepository hotelsRepository;

    @Autowired
    private CompanyRepository companiesRepository;

    @Autowired
    private UserRepository userRepository;

    private Booking completedBooking;

    private Booking bookingWithStatus(BookingStatus status) {
        Company company = companiesRepository.save(Company.builder()
                .name("Acme Hospitality")
                .legalName("Acme Hospitality LLC")
                .email("contact@acme.com")
                .phone("+123456789")
                .address("1 Main St")
                .city("Paris")
                .country("France")
                .website("https://acme.com")
                // Only an approved (ACTIVE) company takes bookings and keeps staff permissions.
                .status(CompanyStatus.ACTIVE)
                .build());

        Hotel hotel = hotelsRepository.save(Hotel.builder()
                .name("Grand Hotel")
                .city("Paris")
                .country("France")
                .address("1 Rue de Rivoli")
                .starRating(4)
                .phone("+123456789")
                .email("contact@grandhotel.com")
                .description("A lovely hotel")
                .imageUrl("http://example.com/image.jpg")
                .company(company)
                .build());

        Room room = roomRepository.save(Room.builder()
                .number("101")
                .type(RoomType.DOUBLE)
                .pricePerNight(new BigDecimal("100.0"))
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
                .roles(Set.of(Role.GUEST))
                .emailVerified(true)
                .enabled(true)
                .build());

        return bookingRepository.save(Booking.builder()
                .user(guest)
                .room(room)
                .checkIn(java.time.LocalDateTime.now().minusDays(5))
                .checkOut(java.time.LocalDateTime.now().minusDays(2))
                .guestCount(2)
                .bookingStatus(status)
                .totalPrice(new BigDecimal("300.0"))
                .build());
    }

    @BeforeEach
    void setUp() {
        completedBooking = bookingWithStatus(BookingStatus.COMPLETED);
    }

    private ReviewRequest requestFor(Long bookingId) {
        ReviewRequest request = new ReviewRequest();
        request.setBookingId(bookingId);
        request.setRating(5);
        request.setComment("Great stay");
        return request;
    }

    @Test
    void create_succeeds_forCompletedBookingWithNoExistingReview() {
        ReviewResponse response = reviewsService.create(requestFor(completedBooking.getId()));

        assertThat(response.getBookingId()).isEqualTo(completedBooking.getId());
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.isApproved()).isFalse();
    }

    @Test
    void create_throws_whenBookingNotCompleted() {
        Booking pendingBooking = bookingWithStatus(BookingStatus.PENDING);

        assertThatThrownBy(() -> reviewsService.create(requestFor(pendingBooking.getId())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reviews can only be submitted for COMPLETED bookings");
    }

    @Test
    void create_throws_whenReviewAlreadyExistsForBooking() {
        reviewsService.create(requestFor(completedBooking.getId()));

        assertThatThrownBy(() -> reviewsService.create(requestFor(completedBooking.getId())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A review already exists for that booking");
    }
}
