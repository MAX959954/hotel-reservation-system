package user;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserServiceImpl userServiceImpl;

    @Mock
    private JwtBlacklistService jwtBlacklistService;

    @InjectMocks
    private JwtAuthFilter filter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private CustomUserDetails approvedUser() {
        User user = User.builder()
                .id(1L)
                .email("jane@example.com")
                .passwordHash("hashed")
                .roles(Set.of(Roles.GUEST))
                .emailVerified(true)
                .accountStatus(AccountStatus.APPROVED)
                .build();
        return new CustomUserDetails(user, List.of());
    }

    private void run(String bearerToken) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (bearerToken != null) {
            request.addHeader("Authorization", "Bearer " + bearerToken);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void authenticates_whenTokenValidAndNotBlacklistedAndAccountApproved() throws Exception {
        given(jwtService.extractEmail("token")).willReturn("jane@example.com");
        given(jwtService.extractJti("token")).willReturn("jti-1");
        given(jwtBlacklistService.isBlacklisted("jti-1")).willReturn(false);
        given(userServiceImpl.loadUserByUsername("jane@example.com")).willReturn(approvedUser());
        given(jwtService.isTokenValid("token", "jane@example.com")).willReturn(true);

        run("token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("jane@example.com");
    }

    @Test
    void doesNotAuthenticate_whenNoAuthorizationHeader() throws Exception {
        run(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // The one case a signature/expiry check alone can never catch: the token is
    // otherwise perfectly valid, but was explicitly revoked (logout).
    @Test
    void doesNotAuthenticate_whenTokenIsBlacklisted() throws Exception {
        given(jwtService.extractEmail("token")).willReturn("jane@example.com");
        given(jwtService.extractJti("token")).willReturn("jti-1");
        given(jwtBlacklistService.isBlacklisted("jti-1")).willReturn(true);

        run("token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // A ban/suspension/lock set after this token was issued must take effect on its very
    // next use, not just once the token eventually expires on its own.
    @Test
    void doesNotAuthenticate_whenAccountNoLongerApproved() throws Exception {
        User user = User.builder()
                .id(1L).email("jane@example.com").passwordHash("hashed")
                .roles(Set.of(Roles.GUEST)).emailVerified(true)
                .accountStatus(AccountStatus.BANNED)
                .build();
        given(jwtService.extractEmail("token")).willReturn("jane@example.com");
        given(jwtService.extractJti("token")).willReturn("jti-1");
        given(jwtBlacklistService.isBlacklisted("jti-1")).willReturn(false);
        given(userServiceImpl.loadUserByUsername("jane@example.com"))
                .willReturn(new CustomUserDetails(user, List.of()));

        run("token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // Same account-status gate, but via emailVerified rather than accountStatus — see
    // CustomUserDetails.isEnabled().
    @Test
    void doesNotAuthenticate_whenEmailNotVerified() throws Exception {
        User user = User.builder()
                .id(1L).email("jane@example.com").passwordHash("hashed")
                .roles(Set.of(Roles.GUEST)).emailVerified(false)
                .accountStatus(AccountStatus.APPROVED)
                .build();
        given(jwtService.extractEmail("token")).willReturn("jane@example.com");
        given(jwtService.extractJti("token")).willReturn("jti-1");
        given(jwtBlacklistService.isBlacklisted("jti-1")).willReturn(false);
        given(userServiceImpl.loadUserByUsername("jane@example.com"))
                .willReturn(new CustomUserDetails(user, List.of()));

        run("token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // A token issued before the account's last forced-logout instant (password change)
    // must be rejected even though its own signature and expiry both still check out.
    @Test
    void doesNotAuthenticate_whenTokenIssuedBeforeTokenValidAfter() throws Exception {
        User user = User.builder()
                .id(1L).email("jane@example.com").passwordHash("hashed")
                .roles(Set.of(Roles.GUEST)).emailVerified(true)
                .accountStatus(AccountStatus.APPROVED)
                .tokenValidAfter(LocalDateTime.now())
                .build();
        given(jwtService.extractEmail("token")).willReturn("jane@example.com");
        given(jwtService.extractJti("token")).willReturn("jti-1");
        given(jwtBlacklistService.isBlacklisted("jti-1")).willReturn(false);
        given(userServiceImpl.loadUserByUsername("jane@example.com"))
                .willReturn(new CustomUserDetails(user, List.of()));
        given(jwtService.extractIssuedAt("token")).willReturn(LocalDateTime.now().minusMinutes(5));

        run("token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void authenticates_whenTokenIssuedAfterTokenValidAfter() throws Exception {
        LocalDateTime validAfter = LocalDateTime.now().minusMinutes(10);
        User user = User.builder()
                .id(1L).email("jane@example.com").passwordHash("hashed")
                .roles(Set.of(Roles.GUEST)).emailVerified(true)
                .accountStatus(AccountStatus.APPROVED)
                .tokenValidAfter(validAfter)
                .build();
        given(jwtService.extractEmail("token")).willReturn("jane@example.com");
        given(jwtService.extractJti("token")).willReturn("jti-1");
        given(jwtBlacklistService.isBlacklisted("jti-1")).willReturn(false);
        given(userServiceImpl.loadUserByUsername("jane@example.com"))
                .willReturn(new CustomUserDetails(user, List.of()));
        given(jwtService.extractIssuedAt("token")).willReturn(LocalDateTime.now()); // minted after the reset
        given(jwtService.isTokenValid("token", "jane@example.com")).willReturn(true);

        run("token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void doesNotAuthenticate_whenJwtServiceThrows() throws Exception {
        given(jwtService.extractEmail(any())).willThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "expired"));

        run("expired-token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
