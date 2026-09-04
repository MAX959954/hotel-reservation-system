package payment;


import java.util.List;

public interface PaymentService {
    PaymentResponse pay(PaymentRequest request);
    /** Card and Google Pay payments: creates a Stripe PaymentIntent and a matching
     *  PENDING Payment row, returning the client secret Stripe.js needs to collect
     *  payment details in the browser. Call {@link #confirm(Long)} once Stripe.js
     *  reports success. */
    PaymentIntentResponse createIntent(PaymentRequest request);
    /** Re-checks the PaymentIntent directly with Stripe (never trusts the client's own
     *  claim of success) and, only if Stripe agrees it succeeded, marks the Payment
     *  COMPLETED and fires the confirmation email. */
    PaymentResponse confirm(Long paymentId);
    PaymentResponse getById(Long id);
    PaymentResponse getByBookingId(Long bookingId);
    List<PaymentResponse> getByStatus(PaymentStatus status);
    List<PaymentResponse> getByMethod(PaymentMethod method);
    List<PaymentResponse> getByStatusAndMethod(PaymentStatus status , PaymentMethod method);
    /** Refunds this payment, in full or in part.
     *  @param amount {@code null} refunds everything still owed (this payment's amount
     *  minus whatever's already been refunded, across any earlier partial refunds);
     *  otherwise refunds exactly that much, which must not exceed what's still owed.
     *  Card/Google Pay payments are refunded through Stripe itself; other methods only
     *  record that staff already returned the money outside this system. A refund that
     *  exhausts the remaining balance also cancels the booking, provided it's still
     *  CONFIRMED (a stay already checked in or completed is left alone — see
     *  PaymentServiceImpl.cancelBookingAfterFullRefund). */
    PaymentResponse refund(Long id, Double amount);
    PaymentResponse cancel(Long id);

    /** Verifies {@code signatureHeader} against {@code stripe.webhook-secret} and
     *  reconciles a payment's status from the Stripe event, independent of any client
     *  call. Covers cases confirm()/refund() can't: the guest's browser dying after
     *  Stripe already charged the card (payment_intent.succeeded), an async decline
     *  (payment_intent.payment_failed), and a refund issued directly from the Stripe
     *  Dashboard rather than through {@link #refund(Long, Double)} (charge.refunded). */
    void handleWebhookEvent(String payload, String signatureHeader);
}
