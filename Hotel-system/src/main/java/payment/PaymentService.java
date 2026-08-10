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
    PaymentResponse refund(Long id );
    PaymentResponse cancel(Long id);

}
