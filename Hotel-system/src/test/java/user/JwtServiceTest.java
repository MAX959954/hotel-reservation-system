package user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // A real HMAC-SHA256 key, base64-encoded the same way jwt.secret is configured in
        // application.yml — getSigningKey() base64-decodes it.
        String secret = Base64.getEncoder().encodeToString("this-is-a-test-signing-key-32-bytes!".getBytes());
        ReflectionTestUtils.setField(jwtService, "secretKey", secret);
        ReflectionTestUtils.setField(jwtService, "expirationMs", 60_000L);
    }

    @Test
    void generateToken_roundTrips_emailAndValidity() {
        String token = jwtService.generateToken("jane@example.com", Set.of(Roles.GUEST));

        assertThat(jwtService.extractEmail(token)).isEqualTo("jane@example.com");
        assertThat(jwtService.isTokenValid(token, "jane@example.com")).isTrue();
    }

    @Test
    void generateToken_assignsADistinctJti_toEachToken() {
        // JwtBlacklistService keys single-token revocation on this — two tokens minted in
        // the same millisecond for the same user must still be distinguishable.
        String first = jwtService.generateToken("jane@example.com", Set.of(Roles.GUEST));
        String second = jwtService.generateToken("jane@example.com", Set.of(Roles.GUEST));

        assertThat(jwtService.extractJti(first))
                .isNotBlank()
                .isNotEqualTo(jwtService.extractJti(second));
    }

    @Test
    void extractIssuedAt_returnsATimestampAroundNow() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(2);
        String token = jwtService.generateToken("jane@example.com", Set.of(Roles.GUEST));
        LocalDateTime after = LocalDateTime.now().plusSeconds(2);

        LocalDateTime issuedAt = jwtService.extractIssuedAt(token);

        assertThat(issuedAt).isAfterOrEqualTo(before.truncatedTo(ChronoUnit.SECONDS)).isBeforeOrEqualTo(after);
    }

    @Test
    void remainingValidity_isCloseToTheFullExpiration_justAfterIssuing() {
        String token = jwtService.generateToken("jane@example.com", Set.of(Roles.GUEST));

        Duration remaining = jwtService.remainingValidity(token);

        assertThat(remaining).isPositive().isLessThanOrEqualTo(Duration.ofMillis(60_000));
        assertThat(remaining).isGreaterThan(Duration.ofMillis(55_000)); // generous slack for test execution time
    }

    // jjwt validates expiration eagerly while parsing (parseSignedClaims itself throws for
    // an already-expired token), before isTokenExpired's own manual check ever runs — so
    // an expired token surfaces as ExpiredJwtException, not a graceful false/zero return,
    // from every method here that has to parse the token first. JwtAuthFilter already
    // relies on exactly this: it catches JwtException broadly and treats it as
    // unauthenticated rather than letting it 500.

    @Test
    void remainingValidity_throws_forAnAlreadyExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "expirationMs", -1L); // already expired the instant it's minted
        String token = jwtService.generateToken("jane@example.com", Set.of(Roles.GUEST));

        assertThatThrownBy(() -> jwtService.remainingValidity(token))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    void isTokenValid_throws_onceExpired() {
        ReflectionTestUtils.setField(jwtService, "expirationMs", -1L);
        String token = jwtService.generateToken("jane@example.com", Set.of(Roles.GUEST));

        assertThatThrownBy(() -> jwtService.isTokenValid(token, "jane@example.com"))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    void isTokenValid_isFalse_forADifferentEmail() {
        String token = jwtService.generateToken("jane@example.com", Set.of(Roles.GUEST));

        assertThat(jwtService.isTokenValid(token, "someone-else@example.com")).isFalse();
    }
}
