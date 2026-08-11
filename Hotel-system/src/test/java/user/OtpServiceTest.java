package user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpCodeRepository otpCodeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MailService mailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(otpService, "expiryMinutes", 10L);
        ReflectionTestUtils.setField(otpService, "resendCooldownSeconds", 30L);
    }

    @Test
    void requestCode_publishesEmailEvent_whenNoRecentCodes() {
        given(otpCodeRepository.findByIdentifierAndCreatedAtAfterOrderByCreatedAtAsc(any(), any())).willReturn(List.of());
        given(passwordEncoder.encode(any())).willReturn("hashed-code");

        otpService.requestCode("Jane@Example.com");

        // Mail must not be sent from inside the transactional method — only once
        // it commits, via the AFTER_COMMIT listener (sendOtpEmail, tested below).
        verify(mailService, never()).sendOtpCode(any(), any());

        ArgumentCaptor<OtpCodeGeneratedEvent> captor = ArgumentCaptor.forClass(OtpCodeGeneratedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().identifier()).isEqualTo("jane@example.com");
        assertThat(captor.getValue().code()).matches("\\d{6}");

        verify(otpCodeRepository).save(any(OtpCode.class));
    }

    @Test
    void requestCode_usesLastListEntryAsMostRecent_relyingOnAscendingOrder() {
        OtpCode older = OtpCode.builder()
                .identifier("jane@example.com")
                .codeHash("x")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();
        OtpCode newer = OtpCode.builder()
                .identifier("jane@example.com")
                .codeHash("x")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .createdAt(LocalDateTime.now())
                .build();
        // Ascending order (oldest first) is exactly what OrderByCreatedAtAsc contracts for —
        // requestCode takes the last list entry as "most recently sent" for the cooldown check.
        given(otpCodeRepository.findByIdentifierAndCreatedAtAfterOrderByCreatedAtAsc(any(), any()))
                .willReturn(List.of(older, newer));

        assertThatThrownBy(() -> otpService.requestCode("jane@example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wait");
    }

    @Test
    void sendOtpEmail_sendsMail_whenEventReceived() {
        otpService.sendOtpEmail(new OtpCodeGeneratedEvent("jane@example.com", "123456"));

        verify(mailService).sendOtpCode("jane@example.com", "123456");
    }

    @Test
    void requestCode_throws_whenResentTooSoon() {
        OtpCode recent = OtpCode.builder()
                .identifier("jane@example.com")
                .codeHash("x")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .createdAt(LocalDateTime.now())
                .build();
        given(otpCodeRepository.findByIdentifierAndCreatedAtAfterOrderByCreatedAtAsc(any(), any())).willReturn(List.of(recent));

        assertThatThrownBy(() -> otpService.requestCode("jane@example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wait");

        verify(mailService, never()).sendOtpCode(any(), any());
    }

    @Test
    void verifyCode_succeeds_whenCodeMatchesAndNotExpired() {
        OtpCode otp = OtpCode.builder()
                .identifier("jane@example.com")
                .codeHash("hashed-code")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attempts(0)
                .build();
        given(otpCodeRepository.findFirstByIdentifierAndConsumedFalseOrderByCreatedAtDesc("jane@example.com"))
                .willReturn(Optional.of(otp));
        given(passwordEncoder.matches("123456", "hashed-code")).willReturn(true);

        String result = otpService.verifyCode("jane@example.com", "123456");

        assertThat(result).isEqualTo("jane@example.com");
        assertThat(otp.isConsumed()).isTrue();
    }

    @Test
    void verifyCode_throws_whenExpired() {
        OtpCode otp = OtpCode.builder()
                .identifier("jane@example.com")
                .codeHash("hashed-code")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .attempts(0)
                .build();
        given(otpCodeRepository.findFirstByIdentifierAndConsumedFalseOrderByCreatedAtDesc("jane@example.com"))
                .willReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verifyCode("jane@example.com", "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verifyCode_incrementsAttempts_whenCodeWrong() {
        OtpCode otp = OtpCode.builder()
                .identifier("jane@example.com")
                .codeHash("hashed-code")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attempts(0)
                .build();
        given(otpCodeRepository.findFirstByIdentifierAndConsumedFalseOrderByCreatedAtDesc("jane@example.com"))
                .willReturn(Optional.of(otp));
        given(passwordEncoder.matches("000000", "hashed-code")).willReturn(false);

        assertThatThrownBy(() -> otpService.verifyCode("jane@example.com", "000000"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Incorrect");

        ArgumentCaptor<OtpCode> captor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepository).save(captor.capture());
        assertThat(captor.getValue().getAttempts()).isEqualTo(1);
    }

    @Test
    void verifyCode_throws_whenTooManyAttempts() {
        OtpCode otp = OtpCode.builder()
                .identifier("jane@example.com")
                .codeHash("hashed-code")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attempts(5)
                .build();
        given(otpCodeRepository.findFirstByIdentifierAndConsumedFalseOrderByCreatedAtDesc("jane@example.com"))
                .willReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verifyCode("jane@example.com", "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Too many");
    }
}
