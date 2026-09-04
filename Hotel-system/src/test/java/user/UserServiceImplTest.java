package user;

import companyuser.CompanyUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private OtpService otpService;

    @Mock
    private GoogleTokenVerifier googleTokenVerifier;

    @Mock
    private CompanyUserService companyUserService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtBlacklistService jwtBlacklistService;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .passwordHash("hashed")
                .roles(Set.of(Roles.GUEST))
                .emailVerified(true)
                .enabled(true)
                .accountStatus(AccountStatus.APPROVED)
                .build();

        // @Value fields aren't populated by @InjectMocks (no Spring context here) — set
        // directly like the framework would at runtime.
        ReflectionTestUtils.setField(userService, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(userService, "lockoutMinutes", 15L);
    }

    private LoginRequest loginRequest(String password) {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("jane@example.com");
        request.setPassword(password);
        return request;
    }

    // ---------- requestOtp ----------

    @Test
    void requestOtp_delegatesToOtpService() {
        userService.requestOtp("jane@example.com");

        verify(otpService).requestCode("jane@example.com");
    }

    // ---------- login ----------

    @Test
    void login_sendsLoginCode_whenPasswordCorrect() {
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("correct-password", "hashed")).willReturn(true);

        userService.login(loginRequest("correct-password"));

        verify(otpService).sendLoginCode("jane@example.com");
    }

    @Test
    void login_throws_whenPasswordWrong() {
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", "hashed")).willReturn(false);

        assertThatThrownBy(() -> userService.login(loginRequest("wrong-password")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Incorrect email or password.");

        verify(otpService, never()).sendLoginCode(any());
    }

    @Test
    void login_throws_whenAccountNotFound() {
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.empty());

        // Same generic message as a wrong password — see login()'s own comment on why
        // "no such account" must not be distinguishable from "wrong password".
        assertThatThrownBy(() -> userService.login(loginRequest("anything")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Incorrect email or password.");
    }

    @Test
    void login_resetsFailedAttempts_onSuccessfulLogin() {
        user.setFailedLoginAttempts(3);
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("correct-password", "hashed")).willReturn(true);

        userService.login(loginRequest("correct-password"));

        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(userRepository).save(user);
    }

    @Test
    void login_locksAccount_afterMaxFailedAttempts() {
        user.setFailedLoginAttempts(4); // one more failure reaches the max of 5
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", "hashed")).willReturn(false);

        assertThatThrownBy(() -> userService.login(loginRequest("wrong-password")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    void login_throws_whenAccountCurrentlyLockedOut_evenWithCorrectPassword() {
        user.setAccountStatus(AccountStatus.LOCKED);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.login(loginRequest("correct-password")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Incorrect email or password.");

        // Locked out short-circuits before the password is even checked.
        verify(passwordEncoder, never()).matches(any(), any());
        verify(otpService, never()).sendLoginCode(any());
    }

    @Test
    void login_autoUnlocks_andSucceeds_oncePastLockedUntil() {
        user.setAccountStatus(AccountStatus.LOCKED);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1)); // window already passed
        user.setFailedLoginAttempts(5);
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("correct-password", "hashed")).willReturn(true);

        userService.login(loginRequest("correct-password"));

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.APPROVED);
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(otpService).sendLoginCode("jane@example.com");
    }

    @Test
    void login_doesNotEscalateOrOverride_anAdminSetAccountStatus() {
        user.setAccountStatus(AccountStatus.BANNED);
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", "hashed")).willReturn(false);

        assertThatThrownBy(() -> userService.login(loginRequest("wrong-password")))
                .isInstanceOf(IllegalStateException.class);

        // This mechanism only ever locks/unlocks accounts it put into that state itself
        // (from APPROVED) — it must never touch a status an admin set for its own reason.
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.BANNED);
        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(userRepository, never()).save(any());
    }

    // ---------- verifyOtp ----------

    @Test
    void verifyOtp_logsInDirectly_whenAccountAlreadyExists() {
        OtpVerifyPayload payload = new OtpVerifyPayload();
        payload.setIdentifier("jane@example.com");
        payload.setCode("123456");

        given(otpService.verifyCode("jane@example.com", "123456")).willReturn("jane@example.com");
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(jwtService.generateToken("jane@example.com", Set.of(Roles.GUEST))).willReturn("token_abc");

        OtpVerifyResponse response = userService.verifyOtp(payload);

        assertThat(response.isNewAccount()).isFalse();
        assertThat(response.getAuth().getToken()).isEqualTo("token_abc");
        assertThat(response.getAuth().getUserId()).isEqualTo(1L);
        assertThat(response.getVerificationTicket()).isNull();
    }

    @Test
    void verifyOtp_returnsVerificationTicket_whenNoAccountYet() {
        OtpVerifyPayload payload = new OtpVerifyPayload();
        payload.setIdentifier("new@example.com");
        payload.setCode("123456");

        given(otpService.verifyCode("new@example.com", "123456")).willReturn("new@example.com");
        given(userRepository.findByEmail("new@example.com")).willReturn(Optional.empty());
        given(jwtService.generateOtpTicket("new@example.com")).willReturn("ticket_abc");

        OtpVerifyResponse response = userService.verifyOtp(payload);

        assertThat(response.isNewAccount()).isTrue();
        assertThat(response.getVerificationTicket()).isEqualTo("ticket_abc");
        assertThat(response.getAuth()).isNull();
    }

    @Test
    void verifyOtp_propagates_whenCodeInvalid() {
        OtpVerifyPayload payload = new OtpVerifyPayload();
        payload.setIdentifier("jane@example.com");
        payload.setCode("000000");

        given(otpService.verifyCode("jane@example.com", "000000"))
                .willThrow(new IllegalStateException("Incorrect code."));

        assertThatThrownBy(() -> userService.verifyOtp(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Incorrect code.");
    }

    // ---------- completeRegistration ----------

    @Test
    void completeRegistration_savesUserAndReturnsToken_whenValid() {
        CompleteRegistrationRequest request = new CompleteRegistrationRequest();
        request.setVerificationTicket("ticket_abc");
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setDateOfBirth(LocalDate.of(1995, 4, 12));
        request.setPassword("password123");

        given(otpService.resolveVerifiedIdentifier("ticket_abc")).willReturn("jane@example.com");
        given(userRepository.existsByEmail("jane@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("hashed");
        given(jwtService.generateToken("jane@example.com", Set.of(Roles.GUEST))).willReturn("token_abc");

        AuthResponse response = userService.completeRegistration(request);

        assertThat(response.getToken()).isEqualTo("token_abc");
        assertThat(response.getEmail()).isEqualTo("jane@example.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getEmail()).isEqualTo("jane@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getDateOfBirth()).isEqualTo(LocalDate.of(1995, 4, 12));
        assertThat(saved.isEmailVerified()).isTrue();
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void completeRegistration_throws_whenAccountAlreadyExists() {
        CompleteRegistrationRequest request = new CompleteRegistrationRequest();
        request.setVerificationTicket("ticket_abc");
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setDateOfBirth(LocalDate.of(1995, 4, 12));
        request.setPassword("password123");

        given(otpService.resolveVerifiedIdentifier("ticket_abc")).willReturn("jane@example.com");
        given(userRepository.existsByEmail("jane@example.com")).willReturn(true);

        assertThatThrownBy(() -> userService.completeRegistration(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
    }

    // ---------- authenticateWithGoogle ----------

    @Test
    void authenticateWithGoogle_createsAccount_whenNoneExists() {
        GoogleTokenVerifier.GoogleIdentity identity = GoogleTokenVerifier.GoogleIdentity.builder()
                .email("jane@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .subject("google-sub-1")
                .build();

        given(googleTokenVerifier.verify("id_token_abc")).willReturn(identity);
        given(userRepository.findByGoogleId("google-sub-1")).willReturn(Optional.empty());
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.empty());
        given(jwtService.generateToken("jane@example.com", Set.of(Roles.GUEST))).willReturn("token_abc");

        AuthResponse response = userService.authenticateWithGoogle("id_token_abc");

        assertThat(response.getToken()).isEqualTo("token_abc");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getGoogleId()).isEqualTo("google-sub-1");
        assertThat(captor.getValue().getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void authenticateWithGoogle_linksExistingAccount_whenEmailMatches() {
        GoogleTokenVerifier.GoogleIdentity identity = GoogleTokenVerifier.GoogleIdentity.builder()
                .email("jane@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .subject("google-sub-1")
                .build();

        given(googleTokenVerifier.verify("id_token_abc")).willReturn(identity);
        given(userRepository.findByGoogleId("google-sub-1")).willReturn(Optional.empty());
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(jwtService.generateToken("jane@example.com", Set.of(Roles.GUEST))).willReturn("token_abc");

        userService.authenticateWithGoogle("id_token_abc");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getGoogleId()).isEqualTo("google-sub-1");
        assertThat(captor.getValue()).isSameAs(user);
    }

    // ---------- loadUserByUsername ----------

    @Test
    void loadUserByUsername_returnsUserDetails_whenFound() {
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));

        UserDetails details = userService.loadUserByUsername("jane@example.com");

        assertThat(details.getUsername()).isEqualTo("jane@example.com");
        assertThat(details.getPassword()).isEqualTo("hashed");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_GUEST");
    }

    @Test
    void loadUserByUsername_accountNonLocked_isFalse_whenLocked() {
        user.setAccountStatus(AccountStatus.LOCKED);
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));

        UserDetails details = userService.loadUserByUsername("jane@example.com");

        assertThat(details.isAccountNonLocked()).isFalse();
    }

    @Test
    void loadUserByUsername_accountNonLocked_isFalse_whenSuspended() {
        // Denylisting only LOCKED/BANNED would silently let this and DEACTIVATED/REJECTED/
        // ANONYMIZED accounts through — the allowlist in loadUserByUsername must catch it too.
        user.setAccountStatus(AccountStatus.SUSPENDED);
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));

        UserDetails details = userService.loadUserByUsername("jane@example.com");

        assertThat(details.isAccountNonLocked()).isFalse();
    }

    @Test
    void loadUserByUsername_throws_whenNotFound() {
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("jane@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("jane@example.com");
    }

    @Test
    void loadUserByUsername_exposesTokenValidAfter_forJwtAuthFilter() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        user.setTokenValidAfter(cutoff);
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));

        UserDetails details = userService.loadUserByUsername("jane@example.com");

        assertThat(details).isInstanceOf(CustomUserDetails.class);
        assertThat(((CustomUserDetails) details).getTokenValidAfter()).isEqualTo(cutoff);
    }

    // ---------- changePassword ----------

    private void authenticateAs(String email) {
        org.springframework.security.core.Authentication authentication =
                org.mockito.Mockito.mock(org.springframework.security.core.Authentication.class);
        given(authentication.getName()).willReturn(email);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void changePassword_updatesHash_whenCurrentPasswordCorrect() {
        authenticateAs("jane@example.com");
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old-password");
        request.setNewPassword("new-password-123");

        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("old-password", "hashed")).willReturn(true);
        given(passwordEncoder.encode("new-password-123")).willReturn("new-hashed");

        userService.changePassword(request);

        assertThat(user.getPasswordHash()).isEqualTo("new-hashed");
    }

    @Test
    void changePassword_throws_whenCurrentPasswordIncorrect() {
        authenticateAs("jane@example.com");
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrong-password");
        request.setNewPassword("new-password-123");

        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", "hashed")).willReturn(false);

        assertThatThrownBy(() -> userService.changePassword(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Current password is incorrect.");

        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    // Password change is the exact scenario a leaked access token needs to stop working
    // for — tokenValidAfter (checked by JwtAuthFilter) and the refresh token store both
    // have to move together, or a leaked refresh token could just mint a fresh access
    // token with a later iat and slip past the tokenValidAfter check entirely.
    @Test
    void changePassword_bumpsTokenValidAfter_andRevokesAllRefreshTokens() {
        authenticateAs("jane@example.com");
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old-password");
        request.setNewPassword("new-password-123");

        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("old-password", "hashed")).willReturn(true);
        given(passwordEncoder.encode("new-password-123")).willReturn("new-hashed");

        LocalDateTime before = LocalDateTime.now();
        userService.changePassword(request);

        assertThat(user.getTokenValidAfter()).isAfterOrEqualTo(before);
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    void changePassword_skipsCurrentPasswordCheck_whenAccountHasNoHashYet() {
        authenticateAs("jane@example.com");
        user.setPasswordHash(null); // Google-only account, never set a password
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setNewPassword("new-password-123");

        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.encode("new-password-123")).willReturn("new-hashed");

        userService.changePassword(request);

        assertThat(user.getPasswordHash()).isEqualTo("new-hashed");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    // ---------- refresh ----------

    @Test
    void refresh_issuesNewAccessAndRefreshToken_whenRefreshTokenValid() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh_old");

        given(refreshTokenService.validateAndConsume("refresh_old")).willReturn(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(jwtService.generateToken("jane@example.com", user.getRoles())).willReturn("access_new");
        given(refreshTokenService.issue(1L)).willReturn("refresh_new");

        AuthResponse response = userService.refresh(request);

        assertThat(response.getToken()).isEqualTo("access_new");
        assertThat(response.getRefreshToken()).isEqualTo("refresh_new");
    }

    @Test
    void refresh_throws_whenRefreshTokenInvalidOrExpired() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh_unknown");

        given(refreshTokenService.validateAndConsume("refresh_unknown")).willReturn(null);

        assertThatThrownBy(() -> userService.refresh(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid or expired");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void refresh_throws_whenAccountNoLongerApproved() {
        // A refresh token surviving a ban/lock must not be usable to mint a fresh access
        // token around it — accountStatus is re-checked on every refresh, not just at login.
        user.setAccountStatus(AccountStatus.BANNED);
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh_old");

        given(refreshTokenService.validateAndConsume("refresh_old")).willReturn(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.refresh(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");
    }

    // ---------- logout ----------

    @Test
    void logout_revokesRefreshToken_andBlacklistsAccessToken() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh_abc");
        given(jwtService.extractJti("access_abc")).willReturn("jti_abc");
        given(jwtService.remainingValidity("access_abc")).willReturn(java.time.Duration.ofMinutes(10));

        userService.logout(request, "access_abc");

        verify(refreshTokenService).validateAndConsume("refresh_abc");
        verify(jwtBlacklistService).blacklist("jti_abc", java.time.Duration.ofMinutes(10));
    }

    @Test
    void logout_onlyRevokesRefreshToken_whenNoAccessTokenProvided() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh_abc");

        userService.logout(request, null);

        verify(refreshTokenService).validateAndConsume("refresh_abc");
        verify(jwtBlacklistService, never()).blacklist(any(), any());
    }

    @Test
    void logout_succeeds_whenAccessTokenIsMalformed() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh_abc");
        given(jwtService.extractJti("garbage")).willThrow(new io.jsonwebtoken.MalformedJwtException("bad token"));

        // Must not propagate — logout is still successful even though there was nothing
        // valid to blacklist.
        userService.logout(request, "garbage");

        verify(refreshTokenService).validateAndConsume("refresh_abc");
        verify(jwtBlacklistService, never()).blacklist(any(), any());
    }
}
