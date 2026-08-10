package user;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    // MAIL_FROM is often left as an empty string rather than unset (e.g. a blank line in
    // .env), and Spring's ${a:b} default only kicks in when a property is absent, not when
    // it's present-but-blank — so the fallback chain has to happen here, not in application.yml.
    @Value("${app.mail.from:}")
    private String configuredFrom;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    public void sendOtpCode(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(resolveFromAddress());
        message.setTo(toEmail);
        message.setSubject("Your Folio verification code");
        message.setText(
                "Your Folio verification code is " + code + ".\n\n" +
                "It expires in 10 minutes. If you didn't request this, you can ignore this email."
        );
        mailSender.send(message);
    }

    private static final DateTimeFormatter STAY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public void sendPaymentConfirmation(String toEmail, String guestName, String hotelName, String roomNumber,
                                         LocalDateTime checkIn, LocalDateTime checkOut, double amount, String currency) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(resolveFromAddress());
            helper.setTo(toEmail);
            helper.setSubject("Payment confirmed — " + hotelName);
            helper.setText(buildPaymentConfirmationHtml(guestName, hotelName, roomNumber, checkIn, checkOut, amount, currency), true);
            mailSender.send(mimeMessage);
        } catch (jakarta.mail.MessagingException e) {
            // Wrapped rather than declared `throws` — the caller (PaymentServiceImpl's async
            // listener) already catches broadly around this call and logs a warning, matching
            // how a SimpleMailMessage send failure would have surfaced before this HTML rewrite.
            throw new IllegalStateException("Could not build payment confirmation email", e);
        }
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

    private String buildPaymentConfirmationHtml(String guestName, String hotelName, String roomNumber,
                                                  LocalDateTime checkIn, LocalDateTime checkOut, double amount, String currency) {
        String rows = detailRow("Room", roomNumber)
                + detailRow("Check-in", STAY_DATE_FORMAT.format(checkIn))
                + detailRow("Check-out", STAY_DATE_FORMAT.format(checkOut))
                + detailRow("Amount paid", String.format(Locale.ENGLISH, "%.2f %s", amount, currency));

        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:32px 16px;background:#f5f5f5;font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e5e5e5;border-radius:16px;">
                    <tr>
                      <td style="padding:40px 40px 8px 40px;">
                        <div style="width:36px;height:36px;border-radius:50%%;background:#111111;color:#e8c88a;text-align:center;line-height:36px;font-family:Georgia,'Times New Roman',serif;font-size:19px;">F</div>
                        <h1 style="margin:24px 0 12px 0;font-size:26px;line-height:1.3;color:#111111;font-weight:600;">Payment confirmed</h1>
                        <p style="margin:0 0 32px 0;font-size:15px;line-height:1.6;color:#444444;">
                          Hi %s — congratulations, your payment went through and your stay at <strong>%s</strong> is booked.
                        </p>
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
                        <p style="margin:0;font-size:12px;line-height:1.6;color:#999999;">You can view or manage this booking any time from "My bookings" on Folio.</p>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(guestName, hotelName, rows);
    }

    private String resolveFromAddress() {
        if (configuredFrom != null && !configuredFrom.isBlank()) return configuredFrom;
        if (smtpUsername != null && !smtpUsername.isBlank()) return smtpUsername;
        return "no-reply@folio.example";
    }
}
