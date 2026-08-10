package payment;

import booking.Booking;
import booking.BookingRepository;
import booking.BookingStatus;
import hotels.Hotels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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
    void refund_movesCompletedPaymentToRefunded() {
        Payment payment = paymentWithStatus(PaymentStatus.COMPLETED);
        given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.refund(10L);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void refund_throws_whenPaymentNotCompleted() {
        given(paymentRepository.findById(10L)).willReturn(Optional.of(paymentWithStatus(PaymentStatus.PENDING)));

        assertThatThrownBy(() -> paymentService.refund(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only COMPLETED payments can be refunded");

        verify(paymentRepository, never()).save(any());
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
