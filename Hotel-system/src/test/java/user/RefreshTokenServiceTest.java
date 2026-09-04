package user;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Against a real Redis (see application.yml's default host/port; no test override exists,
// same as every other cache-touching @SpringBootTest in this codebase) rather than mocked
// — the whole point of this service is Redis's own TTL/atomicity behavior, which a mock
// can't meaningfully stand in for.
@SpringBootTest(classes = org.example.hotelsystem.HotelSystemApplication.class)
@ActiveProfiles("test")
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // Redis writes aren't covered by any @Transactional rollback (there's no JDBC
    // transaction to roll back here) — without this, every run would leave real,
    // 30-day-TTL keys behind in the shared local Redis instance these tests run against.
    @AfterEach
    void cleanUp() {
        var keys = redisTemplate.keys("refresh:*");
        keys.addAll(redisTemplate.keys("refresh-index:*"));
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void issue_thenValidateAndConsume_returnsTheOwningUserId() {
        String token = refreshTokenService.issue(42L);

        Long userId = refreshTokenService.validateAndConsume(token);

        assertThat(userId).isEqualTo(42L);
    }

    @Test
    void validateAndConsume_isSingleUse() {
        String token = refreshTokenService.issue(42L);
        refreshTokenService.validateAndConsume(token);

        // A second use of the same token — a replay of one already rotated away — must
        // fail exactly like an unknown token, not succeed a second time.
        Long secondAttempt = refreshTokenService.validateAndConsume(token);

        assertThat(secondAttempt).isNull();
    }

    @Test
    void validateAndConsume_returnsNull_forAnUnknownToken() {
        assertThat(refreshTokenService.validateAndConsume("not-a-real-token")).isNull();
    }

    @Test
    void revokeAllForUser_invalidatesEveryOutstandingTokenForThatUser_onlyAffectingThem() {
        String tokenA = refreshTokenService.issue(1L);
        String tokenB = refreshTokenService.issue(1L);
        String otherUserToken = refreshTokenService.issue(2L);

        refreshTokenService.revokeAllForUser(1L);

        assertThat(refreshTokenService.validateAndConsume(tokenA)).isNull();
        assertThat(refreshTokenService.validateAndConsume(tokenB)).isNull();
        assertThat(refreshTokenService.validateAndConsume(otherUserToken)).isEqualTo(2L);
    }

    @Test
    void revokeAllForUser_isSafe_whenUserHasNoOutstandingTokens() {
        // No prior issue() call for this id — the index set doesn't even exist yet.
        refreshTokenService.revokeAllForUser(999L);
    }

    @Test
    void issue_setsATtlOnTheUnderlyingRedisKey() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationDays", 30L);
        String token = refreshTokenService.issue(1L);

        Long ttlSeconds = redisTemplate.getExpire("refresh:" + token);

        assertThat(ttlSeconds).isNotNull().isPositive();
    }

    @Test
    void issuedTokens_areUniquePerCall() {
        Set<String> tokens = Set.of(
                refreshTokenService.issue(1L),
                refreshTokenService.issue(1L),
                refreshTokenService.issue(1L));

        assertThat(tokens).hasSize(3);
    }
}
