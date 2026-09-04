package user;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Refresh tokens live only in Redis, never in Postgres — an opaque random string mapped
 * to a user id, TTL'd by Redis itself rather than tracked by hand. Kept deliberately
 * separate from the JWT access token: the access token proves identity for a request,
 * this proves the right to mint a new one, and revoking it (logout, password change) is
 * just deleting a key rather than maintaining a denylist that grows forever.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String TOKEN_KEY_PREFIX = "refresh:";
    private static final String USER_INDEX_PREFIX = "refresh-index:";

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-expiration-days:30}")
    private long refreshExpirationDays;

    /** Issues a new refresh token for this user and records it in that user's index (so
     *  every outstanding token can be found and revoked together — see
     *  {@link #revokeAllForUser}). */
    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        Duration ttl = Duration.ofDays(refreshExpirationDays);
        redisTemplate.opsForValue().set(tokenKey(token), String.valueOf(userId), ttl);
        redisTemplate.opsForSet().add(userIndexKey(userId), token);
        return token;
    }

    /** Single-use: a valid token is consumed (deleted) the moment it's read, so replaying
     *  an already-rotated-away token — a signal something leaked — fails exactly like an
     *  unknown one, rather than quietly succeeding a second time. Also doubles as an
     *  explicit single-session revoke (logout): the caller doesn't have to know the
     *  owning user id up front, and simply discarding the return value is a no-op-safe
     *  "revoke if it exists" for a token that's already gone.
     *  @return the owning user's id, or {@code null} if the token is unknown, already
     *  used, expired, or was revoked. */
    public Long validateAndConsume(String token) {
        String key = tokenKey(token);
        String userIdValue = redisTemplate.opsForValue().get(key);
        if (userIdValue == null) {
            return null;
        }
        Long userId = Long.valueOf(userIdValue);
        redisTemplate.delete(key);
        redisTemplate.opsForSet().remove(userIndexKey(userId), token);
        return userId;
    }

    /** Revokes every outstanding refresh token for this user — password change's "log out
     *  everywhere". Members whose own key already expired are simply no-ops to delete. */
    public void revokeAllForUser(Long userId) {
        String indexKey = userIndexKey(userId);
        Set<String> tokens = redisTemplate.opsForSet().members(indexKey);
        if (tokens != null && !tokens.isEmpty()) {
            redisTemplate.delete(tokens.stream().map(RefreshTokenService::tokenKey).toList());
        }
        redisTemplate.delete(indexKey);
    }

    private static String tokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }

    private static String userIndexKey(Long userId) {
        return USER_INDEX_PREFIX + userId;
    }
}
