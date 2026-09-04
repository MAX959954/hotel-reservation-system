package payment;

import booking.Booking;
import booking.BookingRepository;
import booking.BookingStatus;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import user.MailService;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    // These three go through Stripe (createIntent + confirm); the rest — bank transfer,
    // cash on arrival, crypto — are "pay later / pay offline" methods no gateway can
    // charge on our behalf, so pay() below still records them directly, same as Airbnb's
    // own "pay at property" options never touch a card processor either.
    private static final Set<PaymentMethod> GATEWAY_METHODS =
            EnumSet.of(PaymentMethod.CREDIT_CARD, PaymentMethod.DEBIT_CARD, PaymentMethod.GOOGLE_PAY);

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final MailService mailService;
    private final ApplicationEventPublisher eventPublisher;

    // Not part of the Lombok constructor on purpose: it's only needed by
    // handleWebhookEvent, and leaving it as plain field injection keeps that one
    // Stripe-dashboard-configured value out of every other constructor call/test in
    // this class (same blank-safe convention as StripeConfig.secretKey).
    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Override
    @Transactional
    public PaymentResponse pay(PaymentRequest request) {
        if (GATEWAY_METHODS.contains(request.getMethod())) {
            throw new IllegalStateException("Card and Google Pay payments must go through /api/payments/intent");
        }

        Booking booking = findByBookingId(request.getBookingId());
        assertPayable(booking);

        Payment payment = Payment.builder()
                .booking(booking)
                .amount(booking.getTotalPrice())
                .method(request.getMethod())
                .currency(request.getCurrency())
                .transaction_id(request.getTransactionId())
                .status(PaymentStatus.COMPLETED)
                .paidAt(LocalDateTime.now())
                .build();

        Payment saved = paymentRepository.save(payment);
        publishConfirmation(booking, saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public PaymentIntentResponse createIntent(PaymentRequest request, String idempotencyKey) {
        if (!GATEWAY_METHODS.contains(request.getMethod())) {
            throw new IllegalStateException("Only card and Google Pay payments use /api/payments/intent");
        }

        Booking booking = findByBookingId(request.getBookingId());
        assertPayable(booking);

        try {
            // Explicitly "card" only — NOT automatic_payment_methods. That flag pulls in
            // every payment method enabled on the Stripe Dashboard (Klarna, EPS, MB WAY,
            // Bancontact, Link's "save my info" upsell, ...), which is Stripe's own
            // checkout-page assortment, not this app's. Google Pay still works fine over
            // this: it's a wallet UI in front of Stripe, not a distinct PaymentIntent type —
            // the Payment Request Button confirms it as a "card" payment method too.
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(Math.round(booking.getTotalPrice() * 100))
                    .setCurrency("eur")
                    .addPaymentMethodType("card")
                    .putMetadata("bookingId", String.valueOf(booking.getId()))
                    .putMetadata("method", request.getMethod().name())
                    .build();
            // Caller-supplied, not derived from the booking: unlike refund() there's no
            // existing row yet to key off, and a static booking-based key would be actively
            // wrong here — assertPayable() above may have just cancelled a previous
            // PaymentIntent for this booking, and Stripe's idempotency replay returns the
            // ORIGINAL response verbatim, so a reused key would hand back a client_secret
            // for an intent that's already cancelled. Only trustworthy when it's guaranteed
            // fresh per genuine new attempt, which only the caller can guarantee.
            PaymentIntent intent = (idempotencyKey != null && !idempotencyKey.isBlank())
                    ? PaymentIntent.create(params, RequestOptions.builder().setIdempotencyKey(idempotencyKey).build())
                    : PaymentIntent.create(params);

            Payment payment = Payment.builder()
                    .booking(booking)
                    .amount(booking.getTotalPrice())
                    .method(request.getMethod())
                    .currency("EUR")
                    .transaction_id(intent.getId())
                    .status(PaymentStatus.PENDING)
                    .build();
            Payment saved = paymentRepository.save(payment);

            return PaymentIntentResponse.builder()
                    .paymentId(saved.getId())
                    .clientSecret(intent.getClientSecret())
                    .build();
        } catch (StripeException e) {
            throw new IllegalStateException("Could not start payment with Stripe: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public PaymentResponse confirm(Long paymentId) {
        Payment payment = findById(paymentId);

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is not awaiting confirmation");
        }

        try {
            // The only thing that gets a Payment marked COMPLETED is Stripe itself saying
            // so — the frontend calls this right after stripe.confirmPayment() resolves,
            // but that resolution is not treated as proof; a forged or replayed call to
            // this endpoint can't fake a completion Stripe doesn't also report.
            PaymentIntent intent = PaymentIntent.retrieve(payment.getTransaction_id());
            if (!"succeeded".equals(intent.getStatus())) {
                throw new IllegalStateException("Stripe has not confirmed this payment yet (status: " + intent.getStatus() + ")");
            }
        } catch (StripeException e) {
            throw new IllegalStateException("Could not verify payment with Stripe: " + e.getMessage(), e);
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);
        publishConfirmation(payment.getBooking(), saved);
        return toResponse(saved);
    }

    private void assertPayable(Booking booking) {
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Payment is only allowed for confrimed bookings");
        }

        paymentRepository.findByBookingId(booking.getId()).ifPresent(existing -> {
            if (existing.getStatus() == PaymentStatus.COMPLETED) {
                throw new IllegalStateException("A payment already exists for that booking");
            }

            // PENDING (guest opened the card form, a Stripe PaymentIntent got created, then
            // they closed the modal / the card was declined / whatever) or CANCELLED — either
            // way payments.booking_id is UNIQUE, so this leftover row has to be cleared before
            // a retry can be recorded, or every second attempt would 500 on the constraint.
            // Flushed immediately: Hibernate orders inserts before deletes within a flush by
            // default, and the caller's new row would otherwise momentarily collide with this
            // one on the same booking_id.
            if (existing.getStatus() == PaymentStatus.PENDING && existing.getTransaction_id() != null) {
                try {
                    PaymentIntent.retrieve(existing.getTransaction_id()).cancel();
                } catch (StripeException e) {
                    log.warn("Could not cancel stale Stripe PaymentIntent {}: {}", existing.getTransaction_id(), e.getMessage());
                }
            }
            paymentRepository.delete(existing);
            paymentRepository.flush();
        });
    }

    // Published now but only delivered after the transaction commits (see
    // sendConfirmationEmail below) — same reasoning as OtpService's OTP email: the
    // payment row must not depend on mail delivery succeeding, and mail delivery has
    // no business holding the connection the caller's @Transactional method is using.
    private void publishConfirmation(Booking booking, Payment saved) {
        eventPublisher.publishEvent(new PaymentCompletedEvent(
                booking.getUser().getEmail(),
                booking.getUser().getFirstName(),
                booking.getRoom().getHotel().getName(),
                booking.getRoom().getNumber(),
                booking.getCheck_in(),
                booking.getCheck_out(),
                saved.getAmount(),
                saved.getCurrency()
        ));
    }

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendConfirmationEmail(PaymentCompletedEvent event) {
        try {
            mailService.sendPaymentConfirmation(
                    event.email(), event.guestName(), event.hotelName(), event.roomNumber(),
                    event.checkIn(), event.checkOut(), event.amount(), event.currency()
            );
        } catch (Exception e) {
            log.warn("Could not deliver payment confirmation email to {}: {}", event.email(), e.getMessage());
        }
    }

    @Override
    public PaymentResponse getById(Long id){
        return toResponse(findById(id));
    }

    @Override
    public PaymentResponse getByBookingId(Long bookingId) {
        return  toResponse(paymentRepository.findByBookingId(bookingId).orElseThrow(() -> new IllegalStateException("Payment not found for booking id" + bookingId)));
    }

    @Override
    public List<PaymentResponse> getByStatus (PaymentStatus status) {
        return paymentRepository.findByStatus(status).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<PaymentResponse> getByMethod (PaymentMethod method) {
        return paymentRepository.findByMethod(method).stream().map(this :: toResponse).toList();
    }

    @Override
    public List<PaymentResponse> getByStatusAndMethod (PaymentStatus status , PaymentMethod method) {
        return paymentRepository.findByStatusAndMethod(status , method).stream().map(this :: toResponse).toList();
    }


    // Amounts are compared in whole cents (rounded) to avoid floating-point noise (e.g.
    // 149.99999999999997) producing a false "exceeds remaining balance" or a refund that
    // never quite reaches REFUNDED.
    private static final long REMAINING_BALANCE_EPSILON_CENTS = 1;

    @Override
    @Transactional
    public PaymentResponse refund (Long id, Double amount) {
        Payment payment = findById(id);

        if (payment.getStatus() != PaymentStatus.COMPLETED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Only COMPLETED or PARTIALLY_REFUNDED payments can be refunded");
        }
        if (amount != null && amount <= 0) {
            throw new IllegalStateException("Refund amount must be positive");
        }

        double alreadyRefunded = payment.getRefundedAmount() != null ? payment.getRefundedAmount() : 0.0;
        double remaining = payment.getAmount() - alreadyRefunded;
        double refundAmount = amount != null ? amount : remaining;
        long refundAmountCents = Math.round(refundAmount * 100);
        long remainingCents = Math.round(remaining * 100);

        if (refundAmountCents - remainingCents > REMAINING_BALANCE_EPSILON_CENTS) {
            throw new IllegalStateException(
                    "Refund amount " + refundAmount + " exceeds the remaining refundable amount of " + remaining);
        }

        if (GATEWAY_METHODS.contains(payment.getMethod())) {
            // Money for these methods only ever moved through Stripe, so it can only be
            // returned through Stripe too — marking the row REFUNDED without this would
            // show a completed refund on our side while the guest's card is never
            // actually credited (silent money loss / chargeback risk).
            try {
                RefundCreateParams params = RefundCreateParams.builder()
                        .setPaymentIntent(payment.getTransaction_id())
                        .setAmount(refundAmountCents)
                        .build();
                RequestOptions options = RequestOptions.builder()
                        // Keyed on the balance BEFORE this refund, not just the payment id: a
                        // retry of this exact request (same starting balance, same amount)
                        // reuses the key so Stripe dedupes it, but a later, separate partial
                        // refund on top of this one starts from a different balance and gets
                        // its own key — a single per-payment key would make Stripe silently
                        // replay the first refund instead of creating the second one.
                        .setIdempotencyKey("refund-payment-" + payment.getId() + "-"
                                + Math.round(alreadyRefunded * 100) + "-" + refundAmountCents)
                        .build();
                Refund refund = Refund.create(params, options);
                payment.setRefund_transaction_id(refund.getId());
            } catch (StripeException e) {
                throw new IllegalStateException("Could not refund payment with Stripe: " + e.getMessage(), e);
            }
        }
        // Offline methods (bank transfer, cash, crypto) were never charged through a
        // gateway — same as pay(), the money movement happens outside this system and
        // this call only records that staff already returned it.

        double newRefundedTotal = alreadyRefunded + refundAmount;
        payment.setRefundedAmount(newRefundedTotal);
        boolean fullyRefunded = Math.round(payment.getAmount() * 100) - Math.round(newRefundedTotal * 100)
                <= REMAINING_BALANCE_EPSILON_CENTS;
        payment.setStatus(fullyRefunded ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        Payment saved = paymentRepository.save(payment);

        if (fullyRefunded) {
            cancelBookingAfterFullRefund(payment.getBooking());
        }

        return toResponse(saved);
    }

    // A full refund reads as "this booking isn't happening" — but only while it's still
    // just CONFIRMED. CHECKED_IN/COMPLETED reflect a stay that already happened (or is
    // in progress); a refund there is a financial/goodwill matter, not proof the stay
    // didn't occur, so booking status is intentionally left alone. Already-CANCELLED is
    // a no-op (and cancel() would reject it as a terminal state anyway).
    private void cancelBookingAfterFullRefund(Booking booking) {
        if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            booking.setBookingStatus(BookingStatus.CANCELLED);
            booking.setCancelled_at(LocalDateTime.now());
            bookingRepository.save(booking);
        }
    }

    @Override
    public PaymentResponse cancel(Long id) {
        Payment payment =findById(id);

        if(payment.getStatus() != PaymentStatus.PENDING){
            throw new IllegalStateException("Only PENDING payments can be cancelled");
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        return toResponse(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    public void markExpired(Long id) {
        Payment payment = findById(id);

        if (payment.getStatus() != PaymentStatus.PENDING) {
            // Resolved some other way (paid, cancelled, reconciled by the webhook) since
            // the scheduler queried for stale rows — nothing to do.
            return;
        }

        if (GATEWAY_METHODS.contains(payment.getMethod()) && payment.getTransaction_id() != null) {
            // Best-effort, same as the stale-PENDING cleanup in assertPayable: leaving an
            // abandoned PaymentIntent open costs nothing on Stripe's side, so a failure
            // here isn't worth failing the whole job over.
            try {
                PaymentIntent.retrieve(payment.getTransaction_id()).cancel();
            } catch (StripeException e) {
                log.warn("Could not cancel stale Stripe PaymentIntent {}: {}", payment.getTransaction_id(), e.getMessage());
            }
        }

        payment.setStatus(PaymentStatus.EXPIRED);
        paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public void handleWebhookEvent(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            // Refusing to process is the safe failure mode here: without a configured
            // secret there is no way to verify this request actually came from Stripe.
            throw new IllegalStateException("Stripe webhook secret is not configured");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new IllegalStateException("Invalid Stripe webhook signature: " + e.getMessage(), e);
        }

        // Absent only when this event's payload can't be resolved against the API
        // version stripe-java was generated for (a Stripe-side/library mismatch) —
        // nothing safe to act on. Acknowledge anyway so Stripe stops retrying a delivery
        // we will never be able to parse.
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (stripeObject == null) {
            log.warn("Could not deserialize Stripe webhook event {} ({})", event.getId(), event.getType());
            return;
        }

        switch (event.getType()) {
            case "payment_intent.succeeded" -> onPaymentIntentSucceeded((PaymentIntent) stripeObject);
            case "payment_intent.payment_failed" -> onPaymentIntentFailed((PaymentIntent) stripeObject);
            case "charge.refunded" -> onChargeRefunded((Charge) stripeObject);
            case "charge.dispute.created" -> onChargeDisputeCreated((Dispute) stripeObject);
            default -> log.debug("Ignoring Stripe webhook event type {}", event.getType());
        }
    }

    // Reconciles a payment whose confirming client call (see confirm()) never arrived —
    // tab closed, network dropped — even though Stripe did charge the card. Guarded on
    // PENDING so a duplicate delivery of the same event, or one arriving after confirm()
    // already ran, is a no-op rather than a double-fire of the confirmation email.
    private void onPaymentIntentSucceeded(PaymentIntent intent) {
        paymentRepository.findByTransactionId(intent.getId()).ifPresent(payment -> {
            if (payment.getStatus() != PaymentStatus.PENDING) {
                return;
            }
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaidAt(LocalDateTime.now());
            Payment saved = paymentRepository.save(payment);
            publishConfirmation(payment.getBooking(), saved);
        });
    }

    private void onPaymentIntentFailed(PaymentIntent intent) {
        paymentRepository.findByTransactionId(intent.getId()).ifPresent(payment -> {
            if (payment.getStatus() != PaymentStatus.PENDING) {
                return;
            }
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        });
    }

    // Reconciles a refund issued directly from the Stripe Dashboard/API rather than
    // through refund() above — the only way our own status (and refunded total) stays
    // correct when someone refunds a guest, fully or partially, without going through
    // this app at all. Guarded on COMPLETED/PARTIALLY_REFUNDED: once REFUNDED there's
    // nothing left Stripe could still be refunding, so further deliveries of this event
    // (Stripe retries, or one that arrives after refund() already recorded the same
    // refund) are a no-op. charge.amount_refunded is Stripe's own running total for the
    // charge, so it's written as-is rather than added to ours — that stays correct
    // regardless of how many partial refunds (ours or dashboard-issued) came before it.
    private void onChargeRefunded(Charge charge) {
        String paymentIntentId = charge.getPaymentIntent();
        if (paymentIntentId == null) {
            return;
        }
        paymentRepository.findByTransactionId(paymentIntentId).ifPresent(payment -> {
            if (payment.getStatus() != PaymentStatus.COMPLETED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
                return;
            }
            long amountRefundedCents = charge.getAmountRefunded() != null ? charge.getAmountRefunded() : 0L;
            boolean fullyRefunded = amountRefundedCents >= Math.round(payment.getAmount() * 100);
            payment.setRefundedAmount(amountRefundedCents / 100.0);
            payment.setStatus(fullyRefunded ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
            latestRefundId(charge).ifPresent(payment::setRefund_transaction_id);
            paymentRepository.save(payment);

            if (fullyRefunded) {
                cancelBookingAfterFullRefund(payment.getBooking());
            }
        });
    }

    // A dispute means the cardholder's bank pulled the money back through their own
    // process, outside anything Stripe or this app initiated — CHARGEBACK is kept
    // distinct from REFUNDED because, unlike a refund, this is contested: the outcome
    // isn't final yet and Stripe may reverse it later. Deliberately does NOT touch
    // BookingStatus (unlike a full refund) — a chargeback needs a human to look at it,
    // not an automatic cancellation of what may well have been a legitimate stay.
    private void onChargeDisputeCreated(Dispute dispute) {
        String paymentIntentId = dispute.getPaymentIntent();
        if (paymentIntentId == null) {
            return;
        }
        paymentRepository.findByTransactionId(paymentIntentId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.CHARGEBACK) {
                return;
            }
            payment.setStatus(PaymentStatus.CHARGEBACK);
            paymentRepository.save(payment);
        });
    }

    private Optional<String> latestRefundId(Charge charge) {
        if (charge.getRefunds() == null || charge.getRefunds().getData() == null || charge.getRefunds().getData().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(charge.getRefunds().getData().get(0).getId());
    }

    private Payment findById(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> new IllegalStateException("Payment not found by id: " + id));
    }

    private Booking findByBookingId (Long id) {
        return bookingRepository.findById(id).orElseThrow(() -> new IllegalStateException("Booking with that id not found" + id));
    }

    public PaymentResponse toResponse(Payment payment){
        return PaymentResponse.builder()
                .id(payment.getId())
                .bookingId(payment.getBooking().getId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .currency(payment.getCurrency())
                .transactionId(payment.getTransaction_id())
                .refundTransactionId(payment.getRefund_transaction_id())
                .refundedAmount(payment.getRefundedAmount())
                .status(payment.getStatus())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
