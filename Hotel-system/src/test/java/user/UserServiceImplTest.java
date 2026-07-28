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

    @InjectMocks
    private UserServiceImpl userService;

    private RegisterRequest registerRequest;
    private LogInRequest logInRequest;
    private User user;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setFirstName("Jane");
        registerRequest.setLastName("Doe");
        registerRequest.setEmail("jane@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setPhone("+123456789");

        logInRequest = new LogInRequest();
        logInRequest.setEmail("jane@example.com");
        logInRequest.setPassword("password123");

        user = User.builder()
                .id(1L)
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .password_hash("hashed")
                .phone("+123456789")
                .roles(Set.of(Roles.GUEST))
                .emailVerified(true)
                .enabled(true)
                .accountStatus(AccountStatus.PENDING)
                .build();
    }

    // ---------- register ----------

    @Test
    void register_savesUserAndReturnsToken_whenValid() {
        given(userRepository.existsByEmail("jane@example.com")).willReturn(false);
        given(userRepository.existsByPhone("+123456789")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("hashed");
        given(jwtService.generateToken("jane@example.com", Set.of(Roles.GUEST))).willReturn("token_abc");

        AuthResponse response = userService.register(registerRequest);

        assertThat(response.getToken()).isEqualTo("token_abc");
        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.getRoles()).containsExactly(Roles.GUEST);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getPassword_hash()).isEqualTo("hashed");
        assertThat(saved.getRoles()).containsExactly(Roles.GUEST);
        assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(saved.isEmailVerified()).isTrue();
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void register_throws_whenEmailAlreadyInUse() {
        given(userRepository.existsByEmail("jane@example.com")).willReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email already in use");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throws_whenPhoneAlreadyInUse() {
        given(userRepository.existsByEmail("jane@example.com")).willReturn(false);
        given(userRepository.existsByPhone("+123456789")).willReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Phone already in use");

        verify(userRepository, never()).save(any());
    }

    // ---------- logIn ----------

    @Test
    void logIn_returnsToken_whenCredentialsValid() {
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "hashed")).willReturn(true);
        given(jwtService.generateToken("jane@example.com", Set.of(Roles.GUEST))).willReturn("token_abc");

        AuthResponse response = userService.logIn(logInRequest);

        assertThat(response.getToken()).isEqualTo("token_abc");
        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.getRoles()).containsExactly(Roles.GUEST);
    }

    @Test
    void logIn_throws_whenUserNotFound() {
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.logIn(logInRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void logIn_throws_whenPasswordInvalid() {
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "hashed")).willReturn(false);

        assertThatThrownBy(() -> userService.logIn(logInRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid password");
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
    void loadUserByUsername_throws_whenNotFound() {
        given(userRepository.findByEmail("jane@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("jane@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("jane@example.com");
    }
}
