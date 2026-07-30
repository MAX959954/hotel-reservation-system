package user;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Verifies a Google Identity Services ID token by asking Google directly rather than
 * pulling in a Google API client library — one HTTPS round trip to Google's own
 * tokeninfo endpoint, which validates the signature and expiry for us.
 */
@Slf4j
@Service
public class GoogleTokenVerifier {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${google.oauth.client-id:}")
    private String expectedClientId;

    @Data
    @Builder
    public static class GoogleIdentity {
        private String email;
        private String firstName;
        private String lastName;
        private String subject;
    }

    public GoogleIdentity verify(String idToken) {
        if (expectedClientId == null || expectedClientId.isBlank()) {
            throw new IllegalStateException("Google sign-in isn't configured yet.");
        }

        Map<String, Object> claims;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Google sign-in failed. Please try again.");
            }
            claims = objectMapper.readValue(response.body(), Map.class);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Google token verification request failed", ex);
            throw new IllegalStateException("Google sign-in failed. Please try again.");
        }

        String audience = (String) claims.get("aud");
        if (!expectedClientId.equals(audience)) {
            throw new IllegalStateException("Google sign-in failed. Please try again.");
        }
        if (!"true".equals(String.valueOf(claims.get("email_verified")))) {
            throw new IllegalStateException("This Google account's email isn't verified.");
        }

        String email = (String) claims.get("email");
        String givenName = (String) claims.getOrDefault("given_name", "");
        String familyName = (String) claims.getOrDefault("family_name", "");
        String subject = (String) claims.get("sub");

        return GoogleIdentity.builder()
                .email(email)
                .firstName(givenName)
                .lastName(familyName)
                .subject(subject)
                .build();
    }
}
