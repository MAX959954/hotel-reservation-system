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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Crosses room/booking/user/hotel repositories against a real (H2) database,
// exercising the overlap-detection query that BookingServiceImplTest can only
// stub around.
@SpringBootTest(classes = HotelSystemApplication.class)
@ActiveProfiles("test")
@Transactional
class BookingIntegrationTest {

    @Autowired
    private BookingService bookingService;

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
                .password_hash("hashed")
                .phone("+100000001")
                .roles(Set.of(Roles.GUEST))
                .emailVerified(true)
                .enabled(true)
                .build());
    }

    private BookingRequest requestFor(LocalDateTime checkIn, LocalDateTime checkOut) {
        BookingRequest request = new BookingRequest();
        request.setRoomId(room.getId());
        request.setUserId(guest.getId());
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
    void create_throws_whenRoomNotAvailable() {
        room.setStatus(RoomStatus.MAINTENANCE);
        roomRepository.save(room);

        assertThatThrownBy(() -> bookingService.create(
                requestFor(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(3))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Room id not available");
    }
}
