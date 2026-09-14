package security;

import booking.Booking;
import booking.BookingRepository;
import booking.BookingService;
import booking.BookingStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import companies.Companies;
import companies.CompaniesRepository;
import hotels.Hotels;
import hotels.HotelsRepository;
import org.example.hotelsystem.HotelSystemApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import reviews.ReviewsRequest;
import reviews.ReviewsResponse;
import reviews.ReviewsService;
import room.Room;
import room.RoomRepository;
import room.RoomStatus;
import room.RoomType;
import user.MailService;
import user.JwtService;
import user.OtpRequestPayload;
import user.Roles;
import user.User;
import user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full-stack security test: real SecurityConfig, MethodSecurityConfig, JwtAuthFilter
// and CompanyAuthorization, business logic mocked out. Verifies the pieces
// @WebMvcTest slices deliberately skip: missing/insufficient tokens get
// rejected by the filter chain and by @PreAuthorize before ever reaching the
// (mocked) service.
@SpringBootTest(classes = HotelSystemApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CompaniesRepository companiesRepository;

    @Autowired
    private HotelsRepository hotelsRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private ReviewsService reviewsService;

    @MockBean
    private MailService mailService;

    private String guestToken;
    private String adminToken;
    private Long guestOwnedBookingId;

    @BeforeEach
    void setUp() {
        User guest = User.builder()
                .firstName("Gail")
                .lastName("Guest")
                .email("gail.guest@example.com")
                .passwordHash("hashed")
                .phone("+100000001")
                .roles(Set.of(Roles.GUEST))
                .emailVerified(true)
                .enabled(true)
                .build();
        userRepository.save(guest);
        guestToken = jwtService.generateToken(guest.getEmail(), guest.getRoles());

        // POST /api/reviews is now gated on @companyAuth.isBookingOwner(#request.bookingId)
        // (see ReviewsController) — a real booking belonging to `guest` so that check has
        // something to find, rather than a bare literal id nothing in the DB matches.
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

        Booking booking = bookingRepository.save(Booking.builder()
                .user(guest)
                .room(room)
                .check_in(LocalDateTime.now().plusDays(1))
                .check_out(LocalDateTime.now().plusDays(3))
                .guestCount(2)
                .totalPrice(200.0)
                .bookingStatus(BookingStatus.COMPLETED)
                .build());
        guestOwnedBookingId = booking.getId();

        User admin = User.builder()
                .firstName("Alice")
                .lastName("Admin")
                .email("alice.admin@example.com")
                .passwordHash("hashed")
                .phone("+100000002")
                .roles(Set.of(Roles.ADMIN))
                .emailVerified(true)
                .enabled(true)
                .build();
        userRepository.save(admin);
        adminToken = jwtService.generateToken(admin.getEmail(), admin.getRoles());
    }

    @Test
    void adminOnlyEndpoint_returns401_withoutToken() throws Exception {
        // RestAuthenticationEntryPoint rejects a missing/invalid token as 401 (distinct
        // from a 403 @PreAuthorize denial), so the frontend's session-expiry handling fires
        mockMvc.perform(get("/api/bookings/status/{status}", BookingStatus.PENDING))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminOnlyEndpoint_returns403_forInsufficientRole() throws Exception {
        mockMvc.perform(get("/api/bookings/status/{status}", BookingStatus.PENDING)
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminOnlyEndpoint_returns200_forAdmin() throws Exception {
        given(bookingService.getByStatus(BookingStatus.PENDING)).willReturn(List.of());

        mockMvc.perform(get("/api/bookings/status/{status}", BookingStatus.PENDING)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedOnlyEndpoint_returns401_withoutToken() throws Exception {
        ReviewsRequest request = new ReviewsRequest();
        request.setBookingId(1L);
        request.setRating(5);

        mockMvc.perform(post("/api/reviews")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedOnlyEndpoint_returns201_forTheBookingsOwnGuest() throws Exception {
        ReviewsRequest request = new ReviewsRequest();
        request.setBookingId(guestOwnedBookingId);
        request.setRating(5);

        given(reviewsService.create(any(ReviewsRequest.class))).willReturn(ReviewsResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/reviews")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isCreated());
    }

    // POST /api/reviews is gated on @companyAuth.isBookingOwner(#request.bookingId) — a
    // guest can only review their own booking, not one belonging to someone else. Without
    // this, any authenticated user could post a review against a stranger's completed stay
    // just by guessing its booking id.
    @Test
    void authenticatedOnlyEndpoint_returns403_forSomeoneElsesBooking() throws Exception {
        User otherGuest = User.builder()
                .firstName("Owen")
                .lastName("Other")
                .email("owen.other@example.com")
                .passwordHash("hashed")
                .phone("+100000003")
                .roles(Set.of(Roles.GUEST))
                .emailVerified(true)
                .enabled(true)
                .build();
        userRepository.save(otherGuest);
        String otherGuestToken = jwtService.generateToken(otherGuest.getEmail(), otherGuest.getRoles());

        ReviewsRequest request = new ReviewsRequest();
        request.setBookingId(guestOwnedBookingId);
        request.setRating(5);

        mockMvc.perform(post("/api/reviews")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + otherGuestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidToken_isTreatedAsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/bookings/status/{status}", BookingStatus.PENDING)
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otpRequestEndpoint_isPermitAll_withoutToken() throws Exception {
        OtpRequestPayload request = new OtpRequestPayload();
        request.setIdentifier("new.user@example.com");

        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }
}
