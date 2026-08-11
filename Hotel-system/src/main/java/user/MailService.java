package user;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sends through Resend's HTTP API (port 443) rather than raw SMTP. Railway — like most
 * PaaS hosts on their free/trial tiers — blocks outbound SMTP (port 587/465) to stop
 * their infrastructure being used for spam, so JavaMailSender's SMTP connection to Gmail
 * just times out there even though the same credentials work fine locally. An HTTPS API
 * call goes out over the same port every other outbound request already uses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private static final URI RESEND_ENDPOINT = URI.create("https://api.resend.com/emails");
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    // MAIL_FROM is often left as an empty string rather than unset (e.g. a blank line in
    // .env), and Spring's ${a:b} default only kicks in when a property is absent, not when
    // it's present-but-blank — so the fallback chain has to happen here, not in application.yml.
    @Value("${app.mail.from:}")
    private String configuredFrom;

    @Value("${resend.api-key:}")
    private String resendApiKey;

    public void sendOtpCode(String toEmail, String code) {
        send(toEmail, "Your Folio verification code", null,
                "Your Folio verification code is " + code + ".\n\n" +
                "It expires in 10 minutes. If you didn't request this, you can ignore this email.");
    }

    private static final DateTimeFormatter STAY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public void sendPaymentConfirmation(String toEmail, String guestName, String hotelName, String roomNumber,
                                         LocalDateTime checkIn, LocalDateTime checkOut, double amount, String currency) {
        String rows = detailRow("Room", roomNumber)
                + detailRow("Check-in", STAY_DATE_FORMAT.format(checkIn))
                + detailRow("Check-out", STAY_DATE_FORMAT.format(checkOut))
                + detailRow("Amount paid", String.format(Locale.ENGLISH, "%.2f %s", amount, currency));

        String html = emailShell(
                "Payment confirmed",
                "Hi %s — congratulations, your payment went through and your stay at <strong>%s</strong> is booked."
                        .formatted(guestName, hotelName),
                rows,
                "You can view or manage this booking any time from \"My bookings\" on Folio."
        );
        send(toEmail, "Payment confirmed — " + hotelName, html, null);
    }

    public void sendCompanyInvite(String toEmail, String companyName, String role) {
        String rows = detailRow("Company", companyName) + detailRow("Role", humaniseRole(role));
        String intro = "You've been invited to join <strong>%s</strong> on Folio.".formatted(companyName);
        String footer = "Sign in (or create an account with this email address) and open \"Manage bookings\" to accept.";
        send(toEmail, "You've been invited to " + companyName + " on Folio", emailShell("You're invited", intro, rows, footer), null);
    }

    public void sendCompanyApplicationApproved(String toEmail, String companyName) {
        String rows = detailRow("Company", companyName) + detailRow("Status", "Active");
        String intro = "Good news — <strong>%s</strong> has been approved and you're now a hotel manager on Folio."
                .formatted(companyName);
        String footer = "Sign in and open \"Manage bookings\" to start adding hotels and inviting staff.";
        send(toEmail, "Approved — " + companyName + " is live on Folio", emailShell("Application approved", intro, rows, footer), null);
    }

    public void sendCompanyApplicationRejected(String toEmail, String companyName, String reason) {
        String rows = detailRow("Company", companyName)
                + detailRow("Reason", reason == null || reason.isBlank() ? "Not specified" : reason);
        String intro = "We're unable to approve the application for <strong>%s</strong> at this time."
                .formatted(companyName);
        String footer = "You're welcome to submit a new application once the issue above is addressed.";
        send(toEmail, "Update on your Folio application — " + companyName, emailShell("Application not approved", intro, rows, footer), null);
    }

    private String humaniseRole(String role) {
        String lower = role.replace('_', ' ').toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** Row = an uppercase gray label over a black value, mirroring how Airbnb's own
     *  transactional emails lay out "Date and time" / "Location" style detail blocks. */
    private String detailRow(String label, String value) {
        return """
                <tr>
                  <td style="padding:0 0 20px 0;">
                    <div style="font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:0.06em;color:#8a8a8a;margin-bottom:5px;">%s</div>
                    <div style="font-size:15px;color:#111111;">%s</div>
                  </td>
                </tr>
                """.formatted(label, value);
    }

    /** The shared badge/heading/hr/detail-rows-table/footer envelope every transactional
     *  email on Folio uses — see detailRow() for what fills the rows table. */
    private String emailShell(String heading, String introHtml, String rowsHtml, String footerText) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:32px 16px;background:#f5f5f5;font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e5e5e5;border-radius:16px;">
                    <tr>
                      <td style="padding:40px 40px 8px 40px;">
                        <div style="width:36px;height:36px;border-radius:50%%;background:#111111;color:#e8c88a;text-align:center;line-height:36px;font-family:Georgia,'Times New Roman',serif;font-size:19px;">F</div>
                        <h1 style="margin:24px 0 12px 0;font-size:26px;line-height:1.3;color:#111111;font-weight:600;">%s</h1>
                        <p style="margin:0 0 32px 0;font-size:15px;line-height:1.6;color:#444444;">%s</p>
                      </td>
                    </tr>
                    <tr><td style="padding:0 40px;"><hr style="border:none;border-top:1px solid #ececec;margin:0 0 24px 0;"/></td></tr>
                    <tr>
                      <td style="padding:0 40px;">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">%s</table>
                      </td>
                    </tr>
                    <tr><td style="padding:0 40px;"><hr style="border:none;border-top:1px solid #ececec;margin:4px 0 24px 0;"/></td></tr>
                    <tr>
                      <td style="padding:0 40px 40px 40px;">
                        <p style="margin:0;font-size:12px;line-height:1.6;color:#999999;">%s</p>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(heading, introHtml, rowsHtml, footerText);
    }

    private void send(String toEmail, String subject, String html, String text) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", resolveFromAddress());
        payload.put("to", toEmail);
        payload.put("subject", subject);
        if (html != null) payload.put("html", html);
        if (text != null) payload.put("text", text);

        try {
            HttpRequest request = HttpRequest.newBuilder(RESEND_ENDPOINT)
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Resend API returned " + response.statusCode() + ": " + response.body());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not send email via Resend", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not send email via Resend", e);
        }
    }

    private String resolveFromAddress() {
        if (configuredFrom != null && !configuredFrom.isBlank()) return configuredFrom;
        return "Folio <onboarding@resend.dev>";
    }
}
