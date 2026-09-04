package payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentLifecycleSchedulerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentLifecycleScheduler scheduler;

    @BeforeEach
    void setUp() {
        // @Value fields aren't populated by @InjectMocks (no Spring context here) — set
        // directly like the framework would at runtime.
        ReflectionTestUtils.setField(scheduler, "pendingExpiryHours", 24L);
    }

    private Payment paymentWithId(Long id) {
        return Payment.builder().id(id).build();
    }

    @Test
    void expireStalePendingPayments_expiresEveryStalePayment() {
        given(paymentRepository.findStalePending(any())).willReturn(List.of(paymentWithId(1L), paymentWithId(2L)));

        scheduler.expireStalePendingPayments();

        verify(paymentService).markExpired(1L);
        verify(paymentService).markExpired(2L);
    }

    @Test
    void expireStalePendingPayments_continuesPastAFailure() {
        given(paymentRepository.findStalePending(any())).willReturn(List.of(paymentWithId(1L), paymentWithId(2L)));
        doThrow(new IllegalStateException("boom")).when(paymentService).markExpired(1L);

        scheduler.expireStalePendingPayments();

        // The failure on payment 1 must not stop payment 2 from being processed.
        verify(paymentService).markExpired(2L);
    }

    @Test
    void expireStalePendingPayments_doesNothing_whenNoneAreStale() {
        given(paymentRepository.findStalePending(any())).willReturn(List.of());

        scheduler.expireStalePendingPayments();

        verify(paymentService, never()).markExpired(any());
    }
}
