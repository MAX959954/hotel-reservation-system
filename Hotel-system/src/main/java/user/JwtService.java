package user;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expirationMs;

    public String generateToken(String email , Set<Roles> roles) {
        return Jwts.builder()
                .subject(email)
                .claim("roles" , roles.stream().map(Enum::name).collect(Collectors.toSet()))
                // Unique per issued token, independent of subject/issuedAt (two tokens for
                // the same user in the same millisecond must still be distinguishable) —
                // this is what JwtBlacklistService/RefreshTokenService key single-token
                // revocation on, since the token itself can't just be deleted like a
                // server-side session would be.
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractJti(String token) {
        return extractClaims(token).getId();
    }

    public LocalDateTime extractIssuedAt(String token) {
        return LocalDateTime.ofInstant(extractClaims(token).getIssuedAt().toInstant(), ZoneId.systemDefault());
    }

    /** How much longer this token has left to live, floored at zero for an already
     *  (or about-to-be) expired one — sized for JwtBlacklistService, whose TTL only
     *  needs to outlive the token itself. */
    public Duration remainingValidity(String token) {
        long remainingMs = extractClaims(token).getExpiration().getTime() - System.currentTimeMillis();
        return Duration.ofMillis(Math.max(remainingMs, 0));
    }

    public boolean isTokenValid (String token , String email) {
        return extractEmail(token).equals(email) && !isTokenExpired(token);
    }

    // Short-lived ticket proving "this identifier just passed OTP verification" — separate
    // from the account JWT above (no roles/session), just enough to carry that fact from
    // the /otp/verify call to the /complete-registration call a moment later.
    private static final String OTP_TICKET_PURPOSE = "otp_verified";
    private static final long OTP_TICKET_TTL_MS = 10 * 60 * 1000;

    public String generateOtpTicket(String identifier) {
        return Jwts.builder()
                .subject(identifier)
                .claim("purpose", OTP_TICKET_PURPOSE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + OTP_TICKET_TTL_MS))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractOtpTicketIdentifier(String ticket) {
        Claims claims = extractClaims(ticket);
        if (!OTP_TICKET_PURPOSE.equals(claims.get("purpose", String.class))) {
            throw new io.jsonwebtoken.JwtException("Not a verification ticket");
        }
        if (claims.getExpiration().before(new Date())) {
            throw new io.jsonwebtoken.JwtException("Verification ticket expired");
        }
        return claims.getSubject();
    }

    private boolean isTokenExpired(String token ) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    //ensures the token was signed by your server and wasn't tampered with
    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }


    //Decodes it from Base64 back to raw bytes
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }
}
