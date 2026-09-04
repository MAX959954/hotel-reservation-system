package user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Revokes one specific already-issued access token (explicit logout) without waiting for
 * its own expiry. Keyed by the token's jti claim, never the token itself — the blacklist
 * entry's TTL is set to exactly the token's own remaining lifetime, so a revoked token's
 * entry disappears from Redis the same moment the token would have stopped being valid
 * anyway; nothing here needs to be swept or grows without bound.
 */
@Service
@RequiredArgsConstructor
public class JwtBlacklistService {

    private static final String KEY_PREFIX = "jwt-blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String jti, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            // Already expired (or expiring this instant) on its own — nothing to revoke.
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}
