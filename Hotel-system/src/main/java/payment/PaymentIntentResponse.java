package payment;

import lombok.Builder;
import lombok.Data;

/** Returned by POST /api/payments/intent — the clientSecret is a one-time-use token
 *  Stripe.js needs in the browser to collect card/wallet details directly with Stripe.
 *  It is not the card data itself and expires once the PaymentIntent is confirmed or
 *  cancelled, so holding onto it server-side isn't useful and it isn't persisted. */
@Data
@Builder
public class PaymentIntentResponse {
    private Long paymentId;
    private String clientSecret;
}
