package user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_REQUESTS_PER_HOUR = 5;

    private final OtpCodeRepository otpCodeRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.otp.expiry-minutes:10}")
    private long expiryMinutes;

    @Value("${app.otp.resend-cooldown-seconds:30}")
    private long resendCooldownSeconds;

    /**
     * Entry point for the registration flow's own OTP step — anyone can hit this
     * unauthenticated, so it must refuse addresses that already have an account. Now that
     * sign-in requires a password (see UserServiceImpl.login), letting this endpoint hand
     * out a login-capable code for an *existing* address regardless of password would
     * quietly recreate the old passwordless login as an unauthenticated bypass.
     */
    @Transactional
    public void requestCode(String identifier) {
        String normalized = normalize(identifier);
        if (userRepository.existsByEmail(normalized)) {
            throw new IllegalStateException("An account already exists for this address — sign in with your password instead.");
        }
        generateAndSendCode(normalized);
    }

    /**
     * Entry point for the second factor of password login — only reachable from
     * UserServiceImpl.login() after the password has already been checked, never directly
     * from a controller. No existence guard here: the caller already confirmed the account
     * exists as part of verifying the password.
     */
    @Transactional
    public void sendLoginCode(String normalizedIdentifier) {
        generateAndSendCode(normalizedIdentifier);
    }

    private void generateAndSendCode(String normalized) {
        LocalDateTime now = LocalDateTime.now();

        List<OtpCode> recent = otpCodeRepository.findByIdentifierAndCreatedAtAfterOrderByCreatedAtAsc(normalized, now.minusHours(1));
        if (!recent.isEmpty()) {
            LocalDateTime lastSentAt = recent.get(recent.size() - 1).getCreatedAt();
            if (lastSentAt.plusSeconds(resendCooldownSeconds).isAfter(now)) {
                throw new IllegalStateException("Please wait before requesting another code.");
            }
        }
        if (recent.size() >= MAX_REQUESTS_PER_HOUR) {
            throw new IllegalStateException("Too many codes requested. Please try again later.");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        OtpCode otp = OtpCode.builder()
                .identifier(normalized)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(now.plusMinutes(expiryMinutes))
                .build();
        otpCodeRepository.save(otp);

        // Published now but only delivered after the transaction commits (see
        // sendOtpEmail below) — the OTP row must never outlive a mail send that
        // never happened, but sending mail also has no business holding the
        // connection this @Transactional method is using.
        eventPublisher.publishEvent(new OtpCodeGeneratedEvent(normalized, code));
    }

    // Deliberately caught rather than left to bubble to the default
    // SimpleAsyncUncaughtExceptionHandler: the OTP row already exists (this listener only
    // fires after that transaction committed), so a broken SMTP config must not look like
    // an unhandled server error — it's a delivery problem, not an application one. One
    // clear log line is what an operator actually needs to notice and fix a bad
    // SMTP_USERNAME/SMTP_PASSWORD; the caller already got its 202 and has no use for this.
    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendOtpEmail(OtpCodeGeneratedEvent event) {
        try {
            mailService.sendOtpCode(event.identifier(), event.code());
        } catch (Exception e) {
            log.warn("Could not deliver OTP email to {}: {}", event.identifier(), e.getMessage());
        }
    }

    /**
     * @return the normalized identifier, once the code is confirmed to belong to it.
     */
    @Transactional
    public String verifyCode(String identifier, String code) {
        String normalized = normalize(identifier);

        OtpCode otp = otpCodeRepository
                .findFirstByIdentifierAndConsumedFalseOrderByCreatedAtDesc(normalized)
                .orElseThrow(() -> new IllegalStateException("No active code for this address. Request a new one."));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("This code has expired. Request a new one.");
        }
        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalStateException("Too many incorrect attempts. Request a new code.");
        }

        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpCodeRepository.save(otp);
            throw new IllegalStateException("Incorrect code.");
        }

        otp.setConsumed(true);
        otpCodeRepository.save(otp);

        return normalized;
    }

    public String resolveVerifiedIdentifier(String ticket) {
        try {
            return jwtService.extractOtpTicketIdentifier(ticket);
        } catch (Exception ex) {
            throw new IllegalStateException("Verification expired — start again.");
        }
    }

    private String normalize(String identifier) {
        return identifier.trim().toLowerCase();
    }
}
