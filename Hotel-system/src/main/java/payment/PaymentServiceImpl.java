package payment;

import booking.Booking;
import booking.BookingRepository;
import booking.BookingStatus;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public PaymentIntentResponse createIntent(PaymentRequest request) {
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
            PaymentIntent intent = PaymentIntent.create(params);

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


    @Override
    public PaymentResponse refund (Long id) {
        Payment payment = findById(id);

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Only COMPLETED payments can be refunded");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        return  toResponse(paymentRepository.save(payment));
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
                .status(payment.getStatus())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
