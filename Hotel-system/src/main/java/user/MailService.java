package user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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

    private String resolveFromAddress() {
        if (configuredFrom != null && !configuredFrom.isBlank()) return configuredFrom;
        if (smtpUsername != null && !smtpUsername.isBlank()) return smtpUsername;
        return "no-reply@folio.example";
    }
}
