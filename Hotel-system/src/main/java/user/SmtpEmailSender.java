package user;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Plain SMTP delivery, used for local development: the "local" profile points it at the
 * Mailpit container from docker-compose.yml, which accepts every message and shows it in
 * a web inbox (http://localhost:8025) instead of delivering it anywhere. That way every
 * e-mail the app sends — sign-in codes, booking and payment notifications — can be seen
 * and checked without a SendGrid account. The JavaMailSender comes from Spring Boot's
 * mail auto-configuration (spring.mail.host / spring.mail.port).
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "smtp")
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private static final String DEFAULT_FROM = "Folio <no-reply@folio.local>";

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:}")
    private String configuredFrom;

    @Override
    public void send(String toEmail, String subject, String html, String text) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            boolean both = html != null && text != null;
            MimeMessageHelper helper = new MimeMessageHelper(message, both, StandardCharsets.UTF_8.name());
            helper.setFrom(configuredFrom == null || configuredFrom.isBlank() ? DEFAULT_FROM : configuredFrom);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            if (both) {
                helper.setText(text, html);
            } else if (html != null) {
                helper.setText(html, true);
            } else {
                helper.setText(text == null ? "" : text, false);
            }
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            throw new IllegalStateException("Could not send email via SMTP", e);
        }
    }
}
