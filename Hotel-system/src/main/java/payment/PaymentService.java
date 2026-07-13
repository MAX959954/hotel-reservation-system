package payment;


import java.util.List;

public interface PaymentService {
    PaymentResponse pay(PaymentRequest request);
    PaymentResponse getById(Long id);
    PaymentResponse getByBookingId(Long bookingId);
    List<PaymentResponse> getByStatus(PaymentStatus status);
    List<PaymentResponse> getByMethod(PaymentMethod method);
    List<PaymentResponse> getByStatusAndMethod(PaymentStatus status , PaymentMethod method);
    PaymentResponse refund(Long id );
    PaymentResponse cancel(Long id);

}
