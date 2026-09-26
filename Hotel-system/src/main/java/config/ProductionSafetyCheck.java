package config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Fail-fast configuration check for the "prod" profile. Local development deliberately
 * runs with convenient defaults (a public dev JWT key, sign-in codes in the log, SMTP to
 * Mailpit - see application-local.yml). This makes sure none of that can silently reach
 * production: the application refuses to start instead of running insecurely.
 *
 * Hard failures are things that would be a security problem; missing integrations that
 * only disable a feature (e-mail, card payments) are logged as warnings.
 */
@Slf4j
@Component
@Profile("prod")
public class ProductionSafetyCheck {

    /** The public key from application-local.yml / docker-compose.yml. */
    static final String DEV_JWT_SECRET = "ROtT1yoDWN5HCnojHm6ygpUNb7soRNGYqSeaw3qShcM=";

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${app.mail.dev-log-otp:false}")
    private boolean devLogOtp;

    @Value("${app.mail.provider:sendgrid}")
    private String mailProvider;

    @Value("${sendgrid.api-key:}")
    private String sendgridApiKey;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String flywayLocations;

    @PostConstruct
    void verify() {
        List<String> problems = new ArrayList<>();

        if (jwtSecret == null || jwtSecret.isBlank()) {
            problems.add("JWT_SECRET is not set");
        } else if (DEV_JWT_SECRET.equals(jwtSecret.trim())) {
            problems.add("JWT_SECRET is the public local-development key - generate a new one (openssl rand -base64 32)");
        } else {
            try {
                if (Base64.getDecoder().decode(jwtSecret.trim()).length < 32) {
                    problems.add("JWT_SECRET must decode to at least 32 bytes");
                }
            } catch (IllegalArgumentException e) {
                problems.add("JWT_SECRET is not valid Base64");
            }
        }
        if (devLogOtp) {
            problems.add("MAIL_DEV_LOG_OTP must not be enabled in production (it writes sign-in codes to the log)");
        }
        if (flywayLocations != null && flywayLocations.contains("seed-accounts")) {
            problems.add("db/seed-accounts must not be loaded in production (it creates an admin account with a public password)");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration: " + String.join("; ", problems));
        }

        if (!"sendgrid".equalsIgnoreCase(mailProvider)) {
            log.warn("app.mail.provider is '{}' in production - SMTP is usually blocked on PaaS hosts", mailProvider);
        } else if (isBlank(sendgridApiKey) || isBlank(mailFrom)) {
            log.warn("SENDGRID_API_KEY or MAIL_FROM is not set - sign-in codes and notifications cannot be e-mailed");
        }
        if (flywayLocations != null && flywayLocations.contains("db/seed")) {
            log.warn("Demo catalogue (db/seed) is enabled - fine for a portfolio demo, not for real customers");
        }
        if (isBlank(stripeSecretKey)) {
            log.warn("STRIPE_SECRET_KEY is not set - card payments will fail");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
