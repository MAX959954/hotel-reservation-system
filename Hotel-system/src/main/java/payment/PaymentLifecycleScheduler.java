package payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// A guest who starts a card payment and never finishes it (closes the tab, 3DS times
// out, abandons the flow) previously left that Payment PENDING forever — nothing else
// in this app ever revisits it.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentLifecycleScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Value("${app.payments.pending-expiry-hours:24}")
    private long pendingExpiryHours;

    @Scheduled(cron = "0 */15 * * * *")
    public void expireStalePendingPayments() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(pendingExpiryHours);
        List<Payment> stale = paymentRepository.findStalePending(cutoff);
        for (Payment payment : stale) {
            try {
                paymentService.markExpired(payment.getId());
                log.info("Marked stale PENDING payment {} as EXPIRED", payment.getId());
            } catch (Exception e) {
                log.warn("Could not expire payment {}: {}", payment.getId(), e.getMessage());
            }
        }
    }
}
