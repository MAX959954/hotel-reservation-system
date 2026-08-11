package security;

import booking.BookingService;
import booking.BookingStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import user.MailService;
import user.JwtService;
import user.OtpRequestPayload;
import user.Roles;
import user.User;
import user.UserRepository;

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

    @MockBean
    private BookingService bookingService;

    @MockBean
    private ReviewsService reviewsService;

    @MockBean
    private MailService mailService;

    private String guestToken;
    private String adminToken;

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
    void authenticatedOnlyEndpoint_returns201_forAnyAuthenticatedUser() throws Exception {
        ReviewsRequest request = new ReviewsRequest();
        request.setBookingId(1L);
        request.setRating(5);

        given(reviewsService.create(any(ReviewsRequest.class))).willReturn(ReviewsResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/reviews")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isCreated());
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
