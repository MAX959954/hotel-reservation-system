package booking;

import companies.Companies;
import companies.CompaniesRepository;
import hotels.HotelsRepository;
import hotels.Hotels;
import org.example.hotelsystem.HotelSystemApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import room.Room;
import room.RoomRepository;
import room.RoomStatus;
import room.RoomType;
import user.Roles;
import user.User;
import user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Crosses room/booking/user/hotel repositories against a real (H2) database,
// exercising the overlap-detection query that BookingServiceImplTest can only
// stub around.
// create() derives the booking's owner from the authenticated principal (see
// BookingServiceImpl.currentUser()), so this needs a populated SecurityContext even
// though it never goes through MockMvc — @WithMockUser's listener sets that up for
// direct service calls too. Username must match the guest's email created below.
@SpringBootTest(classes = HotelSystemApplication.class)
@ActiveProfiles("test")
@WithMockUser(username = "jane.doe@example.com")
@Transactional
class BookingIntegrationTest {

    @Autowired
    private BookingService bookingService;

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

    private Room room;
    private User guest;

    @BeforeEach
    void setUp() {
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

        room = roomRepository.save(Room.builder()
                .number("101")
                .type(RoomType.DOUBLE)
                .price_per_night(100.0)
                .capacity(2)
                .floor(1)
                .status(RoomStatus.AVAILABLE)
                .hotel(hotel)
                .build());

        guest = userRepository.save(User.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .passwordHash("hashed")
                .phone("+100000001")
                .roles(Set.of(Roles.GUEST))
                .emailVerified(true)
                .enabled(true)
                .build());
    }

    private BookingRequest requestFor(LocalDateTime checkIn, LocalDateTime checkOut) {
        BookingRequest request = new BookingRequest();
        request.setRoomId(room.getId());
        request.setCheckIn(checkIn);
        request.setCheckOut(checkOut);
        request.setGuestCount(2);
        return request;
    }

    @Test
    void create_succeeds_forFirstBookingOnRoom() {
        BookingResponse response = bookingService.create(
                requestFor(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3)));

        assertThat(response.getRoomId()).isEqualTo(room.getId());
        assertThat(response.getBookingStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.getTotalPrice()).isEqualTo(200.0);
    }

    @Test
    void create_throws_whenDatesOverlapAnExistingBooking() {
        bookingService.create(requestFor(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(5)));

        assertThatThrownBy(() -> bookingService.create(
                requestFor(LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(7))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Room is already booked for the selected dates");
    }

    @Test
    void create_succeeds_forNonOverlappingDatesOnSameRoom() {
        bookingService.create(requestFor(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3)));

        BookingResponse second = bookingService.create(
                requestFor(LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(5)));

        assertThat(second.getRoomId()).isEqualTo(room.getId());
    }

    @Test
    void create_throws_whenGuestCountExceedsRoomCapacity() {
        BookingRequest request = requestFor(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3));
        request.setGuestCount(5);

        assertThatThrownBy(() -> bookingService.create(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Guest count exceeds room capacity");
    }

    @Test
    void create_throws_whenRoomUnderMaintenance() {
        room.setStatus(RoomStatus.MAINTENANCE);
        roomRepository.save(room);

        assertThatThrownBy(() -> bookingService.create(
                requestFor(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Room is not available for booking");
    }

    // Regression test: confirming a booking used to flip the room to RESERVED for
    // good, which then blocked booking it for any OTHER, non-overlapping dates too.
    @Test
    void create_succeeds_forLaterDates_afterAnEarlierBookingOnTheRoomWasConfirmed() {
        BookingResponse first = bookingService.create(
                requestFor(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3)));
        bookingService.confirm(first.getId());

        BookingResponse second = bookingService.create(
                requestFor(LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7)));

        assertThat(second.getRoomId()).isEqualTo(room.getId());
        assertThat(second.getBookingStatus()).isEqualTo(BookingStatus.PENDING);
    }

    // Regression test: a CANCELLED booking used to keep blocking its dates forever
    // because findOverlappingBookings ignored booking status entirely.
    @Test
    void create_succeeds_forSameDates_afterEarlierBookingWasCancelled() {
        BookingResponse first = bookingService.create(
                requestFor(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3)));
        bookingService.cancel(first.getId());

        BookingResponse second = bookingService.create(
                requestFor(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3)));

        assertThat(second.getRoomId()).isEqualTo(room.getId());
        assertThat(second.getBookingStatus()).isEqualTo(BookingStatus.PENDING);
    }

    // ---------- BookingLifecycleScheduler queries ----------

    @Test
    void findStalePending_returnsPendingBookings_onlyWhenOlderThanCutoff() {
        bookingService.create(requestFor(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3)));

        // created_at is set to "now" by @PrePersist and can't be backdated (updatable =
        // false), so instead of faking an old row, the cutoff itself is moved: a cutoff
        // in the future makes this just-created row count as "older than cutoff", one in
        // the past does not.
        assertThat(bookingRepository.findStalePending(LocalDateTime.now().plusHours(1))).hasSize(1);
        assertThat(bookingRepository.findStalePending(LocalDateTime.now().minusHours(1))).isEmpty();
    }

    @Test
    void findOverdueForNoShow_returnsOnlyConfirmedBookingsPastCheckIn() {
        BookingResponse overdue = bookingService.create(
                requestFor(LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(1)));
        bookingService.confirm(overdue.getId());

        BookingResponse notYetDue = bookingService.create(
                requestFor(LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7)));
        bookingService.confirm(notYetDue.getId());

        List<Booking> results = bookingRepository.findOverdueForNoShow(LocalDateTime.now());

        assertThat(results).extracting(Booking::getId).containsExactly(overdue.getId());
    }

    @Test
    void findOverdueForNoShow_excludesBookingsNeverConfirmed() {
        // Still PENDING — never confirmed by staff, so however overdue its check-in date
        // is, this isn't a no-show, it's an abandoned request (findStalePending's job).
        bookingService.create(requestFor(LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(1)));

        assertThat(bookingRepository.findOverdueForNoShow(LocalDateTime.now())).isEmpty();
    }
}
