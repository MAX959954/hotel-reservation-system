package payment;

import booking.Booking;
import booking.BookingRepository;
import booking.BookingStatus;
import com.stripe.exception.ApiException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.RefundCollection;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import hotels.Hotels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import room.Room;
import user.MailService;
import user.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private MailService mailService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Booking booking;
    private PaymentRequest request;

    @BeforeEach
    void setUp() {
        // Room/hotel/user are populated (not just id/status/totalPrice) because pay()
        // now publishes a PaymentCompletedEvent built from this chain — an incomplete
        // fixture would NPE inside publishConfirmation rather than testing anything.
        Hotels hotel = Hotels.builder().id(1L).name("Ribeira Riverhouse").build();
        Room room = Room.builder().id(1L).number("101").hotel(hotel).build();
        User user = User.builder().id(1L).email("guest@example.com").firstName("Alex").build();

        booking = Booking.builder()
                .id(1L)
                .bookingStatus(BookingStatus.CONFIRMED)
                .totalPrice(250.0)
                .check_in(LocalDateTime.now().plusDays(1))
                .check_out(LocalDateTime.now().plusDays(3))
                .room(room)
                .user(user)
                .build();

        request = new PaymentRequest();
        request.setBookingId(1L);
        request.setMethod(PaymentMethod.CASH);
        request.setCurrency("USD");
        request.setTransactionId("txn-123");

        // @Value fields aren't populated by @InjectMocks (there's no Spring context in
        // this test), so the webhook secret has to be set directly like the framework
        // would at runtime.
        ReflectionTestUtils.setField(paymentService, "webhookSecret", "whsec_test_secret");
    }

    private Payment paymentWithStatus(PaymentStatus status) {
        return Payment.builder()
                .id(10L)
                .booking(booking)
                .amount(booking.getTotalPrice())
                .method(PaymentMethod.CREDIT_CARD)
                .currency("USD")
                .transaction_id("txn-123")
                .status(status)
                .paidAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ---------- pay ----------

    @Test
    void pay_createsCompletedPayment_whenBookingConfirmedAndUnpaid() {
        given(bookingRepository.findById(1L)).willReturn(Optional.of(booking));
        given(paymentRepository.findByBookingId(1L)).willReturn(Optional.empty());
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        PaymentResponse response = paymentService.pay(request);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getBookingId()).isEqualTo(booking.getId());
        assertThat(response.getAmount())
                .as("amount must come from the booking, not the request")
                .isEqualTo(booking.getTotalPrice());
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.getPaidAt()).isNotNull();

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void pay_throws_forGatewayMethods() {
        request.setMethod(PaymentMethod.CREDIT_CARD);

        assertThatThrownBy(() -> paymentService.pay(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/api/payments/intent");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void pay_throws_whenBookingNotFound() {
        given(bookingRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.pay(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Booking with that id not found");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void pay_throws_whenBookingNotConfirmed() {
        booking.setBookingStatus(BookingStatus.PENDING);
        given(bookingRepository.findById(1L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> paymentService.pay(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confrimed bookings"); // matches production message verbatim

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void pay_throws_whenPaymentAlreadyExistsForBooking() {
        given(bookingRepository.findById(1L)).willReturn(Optional.of(booking));
        given(paymentRepository.findByBookingId(1L)).willReturn(Optional.of(paymentWithStatus(PaymentStatus.COMPLETED)));

        assertThatThrownBy(() -> paymentService.pay(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A payment already exists for that booking");

        verify(paymentRepository, never()).save(any());
    }

    // ---------- getById ----------

    @Test
    void getById_returnsResponse_whenFound() {
        given(paymentRepository.findById(10L)).willReturn(Optional.of(paymentWithStatus(PaymentStatus.COMPLETED)));

        PaymentResponse response = paymentService.getById(10L);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getBookingId()).isEqualTo(1L);
    }

    @Test
    void getById_throws_whenNotFound() {
        given(paymentRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getById(99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment not found by id: 99");
    }

    // ---------- getByBookingId ----------

    @Test
    void getByBookingId_returnsResponse_whenFound() {
        given(paymentRepository.findByBookingId(1L)).willReturn(Optional.of(paymentWithStatus(PaymentStatus.COMPLETED)));

        PaymentResponse response = paymentService.getByBookingId(1L);

        assertThat(response.getBookingId()).isEqualTo(1L);
    }

    @Test
    void getByBookingId_throws_whenNotFound() {
        given(paymentRepository.findByBookingId(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getByBookingId(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment not found for booking id");
    }

    // ---------- filtered lookups ----------

    @Test
    void getByStatus_delegatesToRepository() {
        given(paymentRepository.findByStatus(PaymentStatus.COMPLETED))
                .willReturn(List.of(paymentWithStatus(PaymentStatus.COMPLETED)));

        List<PaymentResponse> result = paymentService.getByStatus(PaymentStatus.COMPLETED);

        assertThat(result).extracting(PaymentResponse::getId).containsExactly(10L);
    }

    @Test
    void getByMethod_delegatesToRepository() {
        given(paymentRepository.findByMethod(PaymentMethod.CREDIT_CARD))
                .willReturn(List.of(paymentWithStatus(PaymentStatus.COMPLETED)));

        List<PaymentResponse> result = paymentService.getByMethod(PaymentMethod.CREDIT_CARD);

        assertThat(result).extracting(PaymentResponse::getMethod).containsExactly(PaymentMethod.CREDIT_CARD);
    }

    @Test
    void getByStatusAndMethod_delegatesToRepository() {
        given(paymentRepository.findByStatusAndMethod(PaymentStatus.COMPLETED, PaymentMethod.CREDIT_CARD))
                .willReturn(List.of(paymentWithStatus(PaymentStatus.COMPLETED)));

        List<PaymentResponse> result =
                paymentService.getByStatusAndMethod(PaymentStatus.COMPLETED, PaymentMethod.CREDIT_CARD);

        assertThat(result).hasSize(1);
        verify(paymentRepository).findByStatusAndMethod(PaymentStatus.COMPLETED, PaymentMethod.CREDIT_CARD);
    }

    // ---------- refund ----------

    @Test
    void refund_callsStripeAndStoresRefundId_forGatewayPayment() {
        // paymentWithStatus() defaults to CREDIT_CARD, a GATEWAY_METHODS entry, so the
        // original charge only ever moved through Stripe — the refund must too.
        // amount = 250.0, matching booking.totalPrice from setUp().
        Payment payment = paymentWithStatus(PaymentStatus.COMPLETED);
        given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        Refund stripeRefund = Mockito.mock(Refund.class);
        given(stripeRefund.getId()).willReturn("re_123");

        try (MockedStatic<Refund> refundStatic = Mockito.mockStatic(Refund.class)) {
            refundStatic.when(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(stripeRefund);

            PaymentResponse response = paymentService.refund(10L, null);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(response.getRefundTransactionId()).isEqualTo("re_123");
            assertThat(response.getRefundedAmount()).isEqualTo(250.0);

            ArgumentCaptor<RefundCreateParams> paramsCaptor = ArgumentCaptor.forClass(RefundCreateParams.class);
            ArgumentCaptor<RequestOptions> optionsCaptor = ArgumentCaptor.forClass(RequestOptions.class);
            refundStatic.verify(() -> Refund.create(paramsCaptor.capture(), optionsCaptor.capture()));
            assertThat(paramsCaptor.getValue().getPaymentIntent()).isEqualTo("txn-123");
            assertThat(paramsCaptor.getValue().getAmount()).isEqualTo(25000L); // full amount in cents
            // Keyed on the pre-refund balance (0 refunded so far) and the requested amount.
            assertThat(optionsCaptor.getValue().getIdempotencyKey()).isEqualTo("refund-payment-10-0-25000");
        }
        verify(paymentRepository).save(payment);
    }

    @Test
    void refund_partial_leavesPaymentPartiallyRefunded_andDoesNotCancelBooking() {
        Payment payment = paymentWithStatus(PaymentStatus.COMPLETED); // amount = 250.0
        given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        Refund stripeRefund = Mockito.mock(Refund.class);
        given(stripeRefund.getId()).willReturn("re_partial");

        try (MockedStatic<Refund> refundStatic = Mockito.mockStatic(Refund.class)) {
            refundStatic.when(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(stripeRefund);

            PaymentResponse response = paymentService.refund(10L, 100.0);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
            assertThat(response.getRefundedAmount()).isEqualTo(100.0);

            ArgumentCaptor<RefundCreateParams> paramsCaptor = ArgumentCaptor.forClass(RefundCreateParams.class);
            refundStatic.verify(() -> Refund.create(paramsCaptor.capture(), any(RequestOptions.class)));
            assertThat(paramsCaptor.getValue().getAmount()).isEqualTo(10000L);
        }

        // A partial refund is a price adjustment, not a cancellation — the booking (still
        // CONFIRMED per setUp()) must be left alone.
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void refund_secondPartialRefund_usesDifferentIdempotencyKeyAndCanCompleteTheBalance() {
        Payment payment = paymentWithStatus(PaymentStatus.PARTIALLY_REFUNDED); // amount = 250.0
        payment.setRefundedAmount(100.0); // a first partial refund already happened
        given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        Refund stripeRefund = Mockito.mock(Refund.class);
        given(stripeRefund.getId()).willReturn("re_second");

        try (MockedStatic<Refund> refundStatic = Mockito.mockStatic(Refund.class)) {
            refundStatic.when(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(stripeRefund);

            // Refunds exactly what's left (150.0) -> fully refunded now.
            PaymentResponse response = paymentService.refund(10L, null);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(response.getRefundedAmount()).isEqualTo(250.0);

            ArgumentCaptor<RequestOptions> optionsCaptor = ArgumentCaptor.forClass(RequestOptions.class);
            refundStatic.verify(() -> Refund.create(any(RefundCreateParams.class), optionsCaptor.capture()));
            // Different from a first-refund key (which would start with "...-0-...") since
            // it's derived from the 100.0 already refunded — reusing the first key would
            // make Stripe replay the first refund instead of processing this one.
            assertThat(optionsCaptor.getValue().getIdempotencyKey()).isEqualTo("refund-payment-10-10000-15000");
        }

        // Now fully refunded -> the still-CONFIRMED booking gets cancelled.
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).save(booking);
    }

    @Test
    void refund_cancelsBooking_onlyWhenStillConfirmed() {
        booking.setBookingStatus(BookingStatus.CHECKED_IN);
        Payment payment = paymentWithStatus(PaymentStatus.COMPLETED);
        payment.setMethod(PaymentMethod.BANK_TRANSFER); // skip Stripe, just exercise the booking rule
        given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        paymentService.refund(10L, null);

        // A guest currently checked in (or who already completed their stay) actually
        // used the room — a refund there is a goodwill/financial matter, not proof the
        // stay never happened, so the booking must not be silently cancelled underneath it.
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void refund_throws_whenAmountExceedsRemainingBalance() {
        Payment payment = paymentWithStatus(PaymentStatus.COMPLETED); // amount = 250.0
        given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refund(10L, 300.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds the remaining refundable amount");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refund_throws_whenAmountNotPositive() {
        given(paymentRepository.findById(10L)).willReturn(Optional.of(paymentWithStatus(PaymentStatus.COMPLETED)));

        assertThatThrownBy(() -> paymentService.refund(10L, 0.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be positive");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refund_doesNotCallStripe_forOfflinePayment() {
        Payment payment = paymentWithStatus(PaymentStatus.COMPLETED);
        payment.setMethod(PaymentMethod.BANK_TRANSFER);
        given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<Refund> refundStatic = Mockito.mockStatic(Refund.class)) {
            PaymentResponse response = paymentService.refund(10L, null);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(response.getRefundTransactionId()).isNull();
            refundStatic.verifyNoInteractions();
        }
    }

    @Test
    void refund_throws_andLeavesPaymentCompleted_whenStripeRefundFails() {
        Payment payment = paymentWithStatus(PaymentStatus.COMPLETED);
        given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));

        try (MockedStatic<Refund> refundStatic = Mockito.mockStatic(Refund.class)) {
            refundStatic.when(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(new ApiException("card issuer declined the refund", null, null, null, null));

            assertThatThrownBy(() -> paymentService.refund(10L, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Could not refund payment with Stripe");
        }

        // Must not be left silently marked REFUNDED when Stripe never actually returned
        // the money — that would hide the failure from staff and the guest.
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refund_throws_whenPaymentNotCompleted() {
        given(paymentRepository.findById(10L)).willReturn(Optional.of(paymentWithStatus(PaymentStatus.PENDING)));

        assertThatThrownBy(() -> paymentService.refund(10L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only COMPLETED or PARTIALLY_REFUNDED payments can be refunded");

        verify(paymentRepository, never()).save(any());
    }

    // ---------- webhook ----------

    private Event stubbedEvent(String type, com.stripe.model.StripeObject dataObject) {
        Event event = Mockito.mock(Event.class);
        EventDataObjectDeserializer deserializer = Mockito.mock(EventDataObjectDeserializer.class);
        given(event.getType()).willReturn(type);
        given(event.getDataObjectDeserializer()).willReturn(deserializer);
        given(deserializer.getObject()).willReturn(Optional.of(dataObject));
        return event;
    }

    @Test
    void handleWebhookEvent_throws_whenSignatureInvalid() {
        try (MockedStatic<Webhook> webhookStatic = Mockito.mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any()))
                    .thenThrow(new SignatureVerificationException("signature mismatch", "bad-sig"));

            assertThatThrownBy(() -> paymentService.handleWebhookEvent("{}", "bad-sig"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid Stripe webhook signature");
        }

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void handleWebhookEvent_throws_whenSecretNotConfigured() {
        ReflectionTestUtils.setField(paymentService, "webhookSecret", "");

        assertThatThrownBy(() -> paymentService.handleWebhookEvent("{}", "sig"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("webhook secret is not configured");
    }

    // Covers a guest whose browser died (tab closed, network dropped) right after Stripe
    // charged the card but before the frontend could call confirm() — without this,
    // that payment stays PENDING forever even though Stripe already has the money.
    @Test
    void handleWebhookEvent_completesPendingPayment_onPaymentIntentSucceeded() {
        Payment payment = paymentWithStatus(PaymentStatus.PENDING);
        payment.setPaidAt(null);
        given(paymentRepository.findByTransactionId("txn-123")).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        PaymentIntent intent = Mockito.mock(PaymentIntent.class);
        given(intent.getId()).willReturn("txn-123");
        Event event = stubbedEvent("payment_intent.succeeded", intent);

        try (MockedStatic<Webhook> webhookStatic = Mockito.mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

            paymentService.handleWebhookEvent("{}", "sig");
        }

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isNotNull();
        verify(eventPublisher).publishEvent(any(PaymentCompletedEvent.class));
    }

    // Idempotency: Stripe may deliver the same event more than once, and this event can
    // also arrive after confirm() already ran for the same payment.
    @Test
    void handleWebhookEvent_ignoresPaymentIntentSucceeded_whenAlreadyCompleted() {
        Payment payment = paymentWithStatus(PaymentStatus.COMPLETED);
        given(paymentRepository.findByTransactionId("txn-123")).willReturn(Optional.of(payment));

        PaymentIntent intent = Mockito.mock(PaymentIntent.class);
        given(intent.getId()).willReturn("txn-123");
        Event event = stubbedEvent("payment_intent.succeeded", intent);

        try (MockedStatic<Webhook> webhookStatic = Mockito.mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

            paymentService.handleWebhookEvent("{}", "sig");
        }

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void handleWebhookEvent_marksFailed_onPaymentIntentPaymentFailed() {
        Payment payment = paymentWithStatus(PaymentStatus.PENDING);
        given(paymentRepository.findByTransactionId("txn-123")).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        PaymentIntent intent = Mockito.mock(PaymentIntent.class);
        given(intent.getId()).willReturn("txn-123");
        Event event = stubbedEvent("payment_intent.payment_failed", intent);

        try (MockedStatic<Webhook> webhookStatic = Mockito.mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

            paymentService.handleWebhookEvent("{}", "sig");
        }

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    // Reconciles a refund a staff member issued straight from the Stripe Dashboard,
    // bypassing refund() entirely — the only path that keeps our status correct then.
    @Test
    void handleWebhookEvent_reconcilesFullRefund_onChargeRefunded() {
        Payment payment = paymentWithStatus(PaymentStatus.COMPLETED); // amount = 250.0
        given(paymentRepository.findByTransactionId("txn-123")).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        Refund stripeRefund = Mockito.mock(Refund.class);
        given(stripeRefund.getId()).willReturn("re_999");
        RefundCollection refundCollection = Mockito.mock(RefundCollection.class);
        given(refundCollection.getData()).willReturn(List.of(stripeRefund));

        Charge charge = Mockito.mock(Charge.class);
        given(charge.getPaymentIntent()).willReturn("txn-123");
        given(charge.getAmountRefunded()).willReturn(25000L); // 250.0 * 100, fully refunded
        given(charge.getRefunds()).willReturn(refundCollection);
        Event event = stubbedEvent("charge.refunded", charge);

        try (MockedStatic<Webhook> webhookStatic = Mockito.mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

            paymentService.handleWebhookEvent("{}", "sig");
        }

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefund_transaction_id()).isEqualTo("re_999");
        assertThat(payment.getRefundedAmount()).isEqualTo(250.0);
        // A refund reaching REFUNDED (however it was issued) still cancels a booking
        // that's still just CONFIRMED — same rule as refund()'s own full-refund path.
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).save(booking);
    }

    @Test
    void handleWebhookEvent_reconcilesPartialRefund_onChargeRefunded() {
        Payment payment = paymentWithStatus(PaymentStatus.COMPLETED); // amount = 250.0
        given(paymentRepository.findByTransactionId("txn-123")).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        RefundCollection refundCollection = Mockito.mock(RefundCollection.class);
        given(refundCollection.getData()).willReturn(List.of());

        Charge charge = Mockito.mock(Charge.class);
        given(charge.getPaymentIntent()).willReturn("txn-123");
        given(charge.getAmountRefunded()).willReturn(10000L); // only 100.0 of 250.0
        given(charge.getRefunds()).willReturn(refundCollection);
        Event event = stubbedEvent("charge.refunded", charge);

        try (MockedStatic<Webhook> webhookStatic = Mockito.mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

            paymentService.handleWebhookEvent("{}", "sig");
        }

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(100.0);
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingRepository, never()).save(any());
    }

    // A payment already REFUNDED (via our own refund() or a prior delivery of this same
    // event) has nothing left Stripe could still be refunding — further deliveries are a
    // no-op.
    @Test
    void handleWebhookEvent_ignoresChargeRefunded_whenPaymentAlreadyFullyRefunded() {
        Payment payment = paymentWithStatus(PaymentStatus.REFUNDED);
        given(paymentRepository.findByTransactionId("txn-123")).willReturn(Optional.of(payment));

        Charge charge = Mockito.mock(Charge.class);
        given(charge.getPaymentIntent()).willReturn("txn-123");
        Event event = stubbedEvent("charge.refunded", charge);

        try (MockedStatic<Webhook> webhookStatic = Mockito.mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

            paymentService.handleWebhookEvent("{}", "sig");
        }

        verify(paymentRepository, never()).save(any());
    }

    // PARTIALLY_REFUNDED, unlike REFUNDED, is NOT a terminal state for this guard: a
    // second refund issued from the Stripe Dashboard on top of an existing partial one
    // must still be reconciled, taking the payment the rest of the way to REFUNDED.
    @Test
    void handleWebhookEvent_reconcilesChargeRefunded_whenPaymentAlreadyPartiallyRefunded() {
        Payment payment = paymentWithStatus(PaymentStatus.PARTIALLY_REFUNDED); // amount = 250.0
        payment.setRefundedAmount(100.0);
        given(paymentRepository.findByTransactionId("txn-123")).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        Charge charge = Mockito.mock(Charge.class);
        given(charge.getPaymentIntent()).willReturn("txn-123");
        given(charge.getAmountRefunded()).willReturn(25000L); // Stripe's own running total: now fully refunded
        Event event = stubbedEvent("charge.refunded", charge);

        try (MockedStatic<Webhook> webhookStatic = Mockito.mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

            paymentService.handleWebhookEvent("{}", "sig");
        }

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(250.0);
    }

    @Test
    void handleWebhookEvent_ignoresUnhandledEventType() {
        Event event = stubbedEvent("customer.created", Mockito.mock(com.stripe.model.StripeObject.class));

        try (MockedStatic<Webhook> webhookStatic = Mockito.mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);

            paymentService.handleWebhookEvent("{}", "sig");
        }

        verifyNoInteractions(paymentRepository);
    }

    // ---------- cancel ----------

    @Test
    void cancel_movesPendingPaymentToCancelled() {
        Payment payment = paymentWithStatus(PaymentStatus.PENDING);
        given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.cancel(10L);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void cancel_throws_whenPaymentNotPending() {
        given(paymentRepository.findById(10L)).willReturn(Optional.of(paymentWithStatus(PaymentStatus.COMPLETED)));

        assertThatThrownBy(() -> paymentService.cancel(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only PENDING payments can be cancelled");

        verify(paymentRepository, never()).save(any());
    }
}
