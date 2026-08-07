package user;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Verifies a Google Identity Services ID token locally against Google's published JWKS
 * (via GoogleIdTokenVerifier, which caches and refreshes the keys itself) rather than
 * calling the tokeninfo endpoint on every login — that endpoint is rate-limited and
 * meant for debugging, not production traffic.
 */
@Slf4j
@Service
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${google.oauth.client-id:}") String expectedClientId) {
        this.verifier = expectedClientId.isBlank()
                ? null
                : new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(expectedClientId))
                        .build();
    }

    @Data
    @Builder
    public static class GoogleIdentity {
        private String email;
        private String firstName;
        private String lastName;
        private String subject;
    }

    public GoogleIdentity verify(String idToken) {
        if (verifier == null) {
            throw new IllegalStateException("Google sign-in isn't configured yet.");
        }

        GoogleIdToken googleIdToken;
        try {
            googleIdToken = verifier.verify(idToken);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException ex) {
            log.warn("Google token verification failed", ex);
            throw new IllegalStateException("Google sign-in failed. Please try again.");
        }

        if (googleIdToken == null) {
            throw new IllegalStateException("Google sign-in failed. Please try again.");
        }

        GoogleIdToken.Payload payload = googleIdToken.getPayload();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new IllegalStateException("This Google account's email isn't verified.");
        }

        String givenName = (String) payload.getOrDefault("given_name", "");
        String familyName = (String) payload.getOrDefault("family_name", "");

        return GoogleIdentity.builder()
                .email(payload.getEmail())
                .firstName(givenName)
                .lastName(familyName)
                .subject(payload.getSubject())
                .build();
    }
}
