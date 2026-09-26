package user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends through SendGrid's HTTP API (port 443) rather than raw SMTP. Railway — like most
 * PaaS hosts on their free/trial tiers — blocks outbound SMTP (port 587/465) to stop
 * their infrastructure being used for spam, so an SMTP connection just times out there
 * even though the same credentials work fine locally. An HTTPS API call goes out over the
 * same port every other outbound request already uses.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "sendgrid", matchIfMissing = true)
public class SendGridEmailSender implements EmailSender {

    private static final URI SENDGRID_ENDPOINT = URI.create("https://api.sendgrid.com/v3/mail/send");
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    // MAIL_FROM is often left as an empty string rather than unset (e.g. a blank line in
    // .env), and Spring's ${a:b} default only kicks in when a property is absent, not when
    // it's present-but-blank — so the check has to happen here, not in application.yml.
    // SendGrid requires this to be a single-sender-verified (or domain-verified) address;
    // an unset/unverified MAIL_FROM fails every send with a 403.
    @Value("${app.mail.from:}")
    private String configuredFrom;

    @Value("${sendgrid.api-key:}")
    private String sendgridApiKey;

    @Override
    public void send(String toEmail, String subject, String html, String text) {
        List<Map<String, Object>> content = new ArrayList<>();
        // SendGrid requires text/plain before text/html when both are present.
        if (text != null) content.add(Map.of("type", "text/plain", "value", text));
        if (html != null) content.add(Map.of("type", "text/html", "value", html));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("personalizations", List.of(Map.of("to", List.of(Map.of("email", toEmail)))));
        payload.put("from", resolveFromAddress());
        payload.put("subject", subject);
        payload.put("content", content);

        try {
            HttpRequest request = HttpRequest.newBuilder(SENDGRID_ENDPOINT)
                    .header("Authorization", "Bearer " + sendgridApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("SendGrid API returned " + response.statusCode() + ": " + response.body());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not send email via SendGrid", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not send email via SendGrid", e);
        }
    }

    // Unlike Resend, SendGrid has no shared sandbox sender to fall back to — the "from"
    // address must always be one you've verified there (Settings -> Sender Authentication),
    // so a blank MAIL_FROM is a misconfiguration to fail loudly on rather than paper over.
    private Map<String, String> resolveFromAddress() {
        if (configuredFrom == null || configuredFrom.isBlank()) {
            throw new IllegalStateException(
                    "MAIL_FROM is not set — SendGrid requires a verified sender address (see resolveFromAddress).");
        }
        int lt = configuredFrom.indexOf('<');
        int gt = configuredFrom.indexOf('>');
        if (lt >= 0 && gt > lt) {
            String name = configuredFrom.substring(0, lt).trim();
            String email = configuredFrom.substring(lt + 1, gt).trim();
            return Map.of("email", email, "name", name);
        }
        return Map.of("email", configuredFrom.trim());
    }
}
