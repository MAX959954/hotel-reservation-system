package user;

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

import java.time.LocalDate;
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
    }

    // ---------- requestOtp ----------

    @Test
    void requestOtp_delegatesToOtpService() {
        userService.requestOtp("jane@example.com");

        verify(otpService).requestCode("jane@example.com");
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
}
