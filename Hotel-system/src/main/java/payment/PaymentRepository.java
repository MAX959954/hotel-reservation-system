package payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBookingId(Long bookingId);

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByCurrency(String currency);

    List<Payment> findByMethod(PaymentMethod method);

    @Query("SELECT p FROM Payment p WHERE p.transaction_id = :transactionId")
    Optional<Payment> findByTransactionId(@Param("transactionId") String transactionId);

    List<Payment> findByStatusAndMethod(PaymentStatus status, PaymentMethod method);

    // Used by PaymentLifecycleScheduler to expire abandoned Stripe PaymentIntents that
    // never resolved — see app.payments.pending-expiry-hours.
    @Query("SELECT p FROM Payment p WHERE p.status = payment.PaymentStatus.PENDING AND p.createdAt < :cutoff")
    List<Payment> findStalePending(@Param("cutoff") LocalDateTime cutoff);
}
