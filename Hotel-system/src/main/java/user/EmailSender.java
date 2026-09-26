package user;

/**
 * Delivers one already-built e-mail. {@link MailService} decides what to send; an
 * implementation decides how. Exactly one implementation is active, chosen by
 * {@code app.mail.provider}: {@link SendGridEmailSender} ("sendgrid", the default, used in
 * production) or {@link SmtpEmailSender} ("smtp", used locally with Mailpit).
 */
public interface EmailSender {

    /**
     * @param html HTML body, or {@code null}
     * @param text plain-text body, or {@code null} (at least one of the two is set)
     * @throws IllegalStateException if the message could not be handed over for delivery
     */
    void send(String toEmail, String subject, String html, String text);
}
