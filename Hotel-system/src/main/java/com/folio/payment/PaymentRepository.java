package com.folio.payment;

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

    @Query("SELECT p FROM Payment p WHERE p.transactionId = :transactionId")
    Optional<Payment> findByTransactionId(@Param("transactionId") String transactionId);

    List<Payment> findByStatusAndMethod(PaymentStatus status, PaymentMethod method);

    // Same purpose as BookingRepository.findCompanyIdById.
    @Query("SELECT p.booking.room.hotel.company.id FROM Payment p WHERE p.id = :id")
    Optional<Long> findCompanyIdById(@Param("id") Long id);

    // Used by PaymentLifecycleScheduler to expire abandoned Stripe PaymentIntents that
    // never resolved — see app.payments.pending-expiry-hours. Gateway methods only:
    // offline payments (cash on arrival, bank transfer, crypto) legitimately stay PENDING
    // until staff mark them received, often days later at check-in.
    @Query("SELECT p FROM Payment p WHERE p.status = com.folio.payment.PaymentStatus.PENDING AND p.createdAt < :cutoff " +
            "AND p.method IN (com.folio.payment.PaymentMethod.CREDIT_CARD, com.folio.payment.PaymentMethod.DEBIT_CARD, com.folio.payment.PaymentMethod.GOOGLE_PAY)")
    List<Payment> findStalePending(@Param("cutoff") LocalDateTime cutoff);
}
