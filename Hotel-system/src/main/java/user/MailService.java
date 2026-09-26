package user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Builds the app's transactional e-mails (subjects, HTML/text bodies). Delivery is
 * delegated to an {@link EmailSender}: SendGrid's HTTPS API in production
 * ({@link SendGridEmailSender}, app.mail.provider=sendgrid, the default) or plain SMTP to
 * a local Mailpit inbox in development ({@link SmtpEmailSender}, app.mail.provider=smtp,
 * set by the "local" profile).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final EmailSender emailSender;

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

    public void sendBookingConfirmed(String toEmail, String guestName, String hotelName, String roomNumber,
                                      LocalDateTime checkIn, LocalDateTime checkOut) {
        String rows = detailRow("Room", roomNumber)
                + detailRow("Check-in", STAY_DATE_FORMAT.format(checkIn))
                + detailRow("Check-out", STAY_DATE_FORMAT.format(checkOut));
        String intro = "Hi %s — your booking at <strong>%s</strong> is confirmed.".formatted(guestName, hotelName);
        String footer = "You can view or manage this booking any time from \"My bookings\" on Folio.";
        send(toEmail, "Booking confirmed — " + hotelName, emailShell("Booking confirmed", intro, rows, footer), null);
    }

    public void sendBookingCancelled(String toEmail, String guestName, String hotelName, String roomNumber,
                                      LocalDateTime checkIn, LocalDateTime checkOut) {
        String rows = detailRow("Room", roomNumber)
                + detailRow("Check-in", STAY_DATE_FORMAT.format(checkIn))
                + detailRow("Check-out", STAY_DATE_FORMAT.format(checkOut));
        String intro = "Hi %s — your booking at <strong>%s</strong> has been cancelled.".formatted(guestName, hotelName);
        String footer = "If a payment was made and a refund applies, it will be processed separately. "
                + "Questions? Reach out from \"My bookings\" on Folio.";
        send(toEmail, "Booking cancelled — " + hotelName, emailShell("Booking cancelled", intro, rows, footer), null);
    }

    public void sendBookingCheckedIn(String toEmail, String guestName, String hotelName, String roomNumber,
                                      LocalDateTime checkIn, LocalDateTime checkOut) {
        String rows = detailRow("Room", roomNumber)
                + detailRow("Check-out", STAY_DATE_FORMAT.format(checkOut));
        String intro = "Hi %s — you're checked in at <strong>%s</strong>. Enjoy your stay!".formatted(guestName, hotelName);
        String footer = "You can view or manage this booking any time from \"My bookings\" on Folio.";
        send(toEmail, "You're checked in — " + hotelName, emailShell("Checked in", intro, rows, footer), null);
    }

    public void sendBookingCompleted(String toEmail, String guestName, String hotelName, String roomNumber,
                                      LocalDateTime checkIn, LocalDateTime checkOut) {
        String rows = detailRow("Room", roomNumber)
                + detailRow("Check-in", STAY_DATE_FORMAT.format(checkIn))
                + detailRow("Check-out", STAY_DATE_FORMAT.format(checkOut));
        String intro = "Hi %s — thanks for staying at <strong>%s</strong>. We hope you enjoyed it!".formatted(guestName, hotelName);
        String footer = "We'd love to hear about your stay — leave a review any time from \"My bookings\" on Folio.";
        send(toEmail, "Thanks for staying at " + hotelName, emailShell("Stay completed", intro, rows, footer), null);
    }

    public void sendBookingNoShow(String toEmail, String guestName, String hotelName, String roomNumber,
                                   LocalDateTime checkIn, LocalDateTime checkOut) {
        String rows = detailRow("Room", roomNumber)
                + detailRow("Check-in", STAY_DATE_FORMAT.format(checkIn))
                + detailRow("Check-out", STAY_DATE_FORMAT.format(checkOut));
        String intro = "Hi %s — we marked your booking at <strong>%s</strong> as a no-show since check-in time has passed."
                .formatted(guestName, hotelName);
        String footer = "If this doesn't look right, reach out from \"My bookings\" on Folio.";
        send(toEmail, "Booking marked as no-show — " + hotelName, emailShell("Marked as no-show", intro, rows, footer), null);
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
        emailSender.send(toEmail, subject, html, text);
    }
}
