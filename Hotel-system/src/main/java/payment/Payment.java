package payment;

import booking.Booking;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private PaymentMethod method;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "transaction_id")
    private String transaction_id;

    // Set only when this payment went through the gateway and was actually refunded via
    // Stripe (see PaymentServiceImpl.refund) — distinct from transaction_id so the
    // original charge/PaymentIntent id is never overwritten and both remain traceable
    // in the Stripe dashboard.
    @Column(name = "refund_transaction_id")
    private String refund_transaction_id;

    // Running total across possibly-multiple partial refunds, so a further partial
    // refund (or the webhook reconciling one issued from the Stripe Dashboard) knows
    // how much of `amount` is still owed rather than assuming this is the first refund.
    @Column(name = "refunded_amount", nullable = false)
    @Builder.Default
    private Double refundedAmount = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }


    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

}
