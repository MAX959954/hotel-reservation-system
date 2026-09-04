package user;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// Against a real Redis, same as RefreshTokenServiceTest — the TTL behavior being tested
// here (a blacklist entry that expires alongside the token it revoked) is exactly what a
// mock can't stand in for.
@SpringBootTest(classes = org.example.hotelsystem.HotelSystemApplication.class)
@ActiveProfiles("test")
class JwtBlacklistServiceTest {

    @Autowired
    private JwtBlacklistService jwtBlacklistService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        var keys = redisTemplate.keys("jwt-blacklist:*");
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void isBlacklisted_isFalse_forAJtiNeverBlacklisted() {
        assertThat(jwtBlacklistService.isBlacklisted("never-seen")).isFalse();
    }

    @Test
    void blacklist_thenIsBlacklisted_isTrue() {
        jwtBlacklistService.blacklist("jti-abc", Duration.ofMinutes(5));

        assertThat(jwtBlacklistService.isBlacklisted("jti-abc")).isTrue();
    }

    @Test
    void blacklist_setsATtlMatchingWhatWasPassedIn() {
        jwtBlacklistService.blacklist("jti-ttl", Duration.ofMinutes(5));

        Long ttlSeconds = redisTemplate.getExpire("jwt-blacklist:jti-ttl");

        assertThat(ttlSeconds).isNotNull().isPositive().isLessThanOrEqualTo(300L);
    }

    @Test
    void blacklist_isANoOp_forAZeroOrNegativeTtl() {
        // An already-expired token needs no revoking — and a zero/negative TTL would be a
        // Redis error (SET ... PX 0) rather than a meaningful "expire immediately".
        jwtBlacklistService.blacklist("jti-expired", Duration.ZERO);
        jwtBlacklistService.blacklist("jti-negative", Duration.ofSeconds(-5));

        assertThat(jwtBlacklistService.isBlacklisted("jti-expired")).isFalse();
        assertThat(jwtBlacklistService.isBlacklisted("jti-negative")).isFalse();
    }
}
