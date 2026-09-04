package user;

import com.fasterxml.jackson.databind.ObjectMapper;
import exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import testsupport.MinimalTestApplication;

import java.time.LocalDate;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@ContextConfiguration(classes = {MinimalTestApplication.class, UserController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    private AuthResponse sampleResponse() {
        return AuthResponse.builder()
                .token("token_abc")
                .email("jane@example.com")
                .roles(Set.of(Roles.GUEST))
                .build();
    }

    private OtpRequestPayload validOtpRequest() {
        OtpRequestPayload request = new OtpRequestPayload();
        request.setIdentifier("jane@example.com");
        return request;
    }

    private OtpVerifyPayload validOtpVerify() {
        OtpVerifyPayload request = new OtpVerifyPayload();
        request.setIdentifier("jane@example.com");
        request.setCode("123456");
        return request;
    }

    private CompleteRegistrationRequest validCompleteRegistration() {
        CompleteRegistrationRequest request = new CompleteRegistrationRequest();
        request.setVerificationTicket("ticket_abc");
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setDateOfBirth(LocalDate.of(1995, 4, 12));
        request.setPassword("password123");
        return request;
    }

    // ---------- /otp/request ----------

    @Test
    void requestOtp_returns202_whenRequestValid() throws Exception {
        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validOtpRequest())))
                .andExpect(status().isAccepted());
    }

    @Test
    void requestOtp_returns400_whenIdentifierNotAnEmail() throws Exception {
        OtpRequestPayload invalid = validOtpRequest();
        invalid.setIdentifier("not-an-email");

        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    // ---------- /otp/verify ----------

    @Test
    void verifyOtp_returns200_withAuth_whenAccountExists() throws Exception {
        given(userService.verifyOtp(any(OtpVerifyPayload.class)))
                .willReturn(OtpVerifyResponse.builder().newAccount(false).auth(sampleResponse()).build());

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validOtpVerify())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newAccount").value(false))
                .andExpect(jsonPath("$.auth.token").value("token_abc"));
    }

    @Test
    void verifyOtp_returns200_withTicket_whenAccountIsNew() throws Exception {
        given(userService.verifyOtp(any(OtpVerifyPayload.class)))
                .willReturn(OtpVerifyResponse.builder().newAccount(true).verificationTicket("ticket_abc").build());

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validOtpVerify())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newAccount").value(true))
                .andExpect(jsonPath("$.verificationTicket").value("ticket_abc"));
    }

    @Test
    void verifyOtp_returns400_whenCodeIsNot6Digits() throws Exception {
        OtpVerifyPayload invalid = validOtpVerify();
        invalid.setCode("12");

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyOtp_returns400_whenCodeIncorrect() throws Exception {
        given(userService.verifyOtp(any(OtpVerifyPayload.class)))
                .willThrow(new IllegalStateException("Incorrect code."));

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validOtpVerify())))
                .andExpect(status().isBadRequest());
    }

    // ---------- /complete-registration ----------

    @Test
    void completeRegistration_returns201_whenRequestValid() throws Exception {
        given(userService.completeRegistration(any(CompleteRegistrationRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(post("/api/auth/complete-registration")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validCompleteRegistration())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("token_abc"));
    }

    @Test
    void completeRegistration_returns400_whenPasswordTooShort() throws Exception {
        CompleteRegistrationRequest invalid = validCompleteRegistration();
        invalid.setPassword("short");

        mockMvc.perform(post("/api/auth/complete-registration")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeRegistration_returns400_whenUnderMinimumAge() throws Exception {
        CompleteRegistrationRequest invalid = validCompleteRegistration();
        invalid.setDateOfBirth(LocalDate.now().minusYears(17));

        mockMvc.perform(post("/api/auth/complete-registration")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    // ---------- /google ----------

    @Test
    void google_returns200_whenTokenValid() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest();
        request.setIdToken("id_token_abc");

        given(userService.authenticateWithGoogle("id_token_abc")).willReturn(sampleResponse());

        mockMvc.perform(post("/api/auth/google")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void google_returns400_whenTokenRejected() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest();
        request.setIdToken("bad_token");

        given(userService.authenticateWithGoogle("bad_token"))
                .willThrow(new IllegalStateException("Google sign-in failed. Please try again."));

        mockMvc.perform(post("/api/auth/google")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---------- /refresh ----------

    @Test
    void refresh_returns200_whenTokenValid() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh_abc");

        given(userService.refresh(any(RefreshTokenRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token_abc"));
    }

    @Test
    void refresh_returns400_whenTokenInvalidOrExpired() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("stale_token");

        given(userService.refresh(any(RefreshTokenRequest.class)))
                .willThrow(new IllegalStateException("Refresh token is invalid or expired. Please sign in again."));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_returns400_whenBodyMissingToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- /logout ----------

    @Test
    void logout_returns204_andPassesBearerToken_whenAuthorizationHeaderPresent() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh_abc");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer access_token_abc")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(userService).logout(eq(request), eq("access_token_abc"));
    }

    @Test
    void logout_returns204_withNullAccessToken_whenNoAuthorizationHeader() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh_abc");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(userService).logout(eq(request), isNull());
    }

    @Test
    void logout_returns400_whenBodyMissingToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
