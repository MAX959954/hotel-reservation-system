package payment;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private Long id;
    private Long bookingId;
    private Double amount;
    private PaymentMethod method;
    private String currency;
    private String transactionId;
    private String refundTransactionId;
    private Double refundedAmount;
    private PaymentStatus status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
