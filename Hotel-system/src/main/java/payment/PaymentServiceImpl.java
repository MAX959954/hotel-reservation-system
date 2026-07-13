package payment;

import booking.Booking;
import booking.BookingRepository;
import booking.BookingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public PaymentResponse pay(PaymentRequest request) {
        Booking booking = findByBookingId(request.getBookingId());

        if(booking.getBookingStatus() !=  BookingStatus.CONFIRMED){
            throw new IllegalStateException("Payment is only allowed for confrimed bookings");
        }

        if (paymentRepository.findByBookingId(booking.getId()).isPresent()) {
            throw new IllegalStateException("A payment already exists for that booking");
        }

        Payment payment = Payment.builder()
                .booking(booking)
                .amount(booking.getTotalPrice())
                .method(request.getMethod())
                .currency(request.getCurrency())
                .transaction_id(request.getTransactionId())
                .status(PaymentStatus.COMPLETED)
                .paidAt(LocalDateTime.now())
                .build();


        return toResponse(paymentRepository.save(payment));
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
