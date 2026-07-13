package payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentRequest {
    @NotNull(message = "bookingId is required")
    private Long bookingId;

    @NotNull(message = "method is required")
    private PaymentMethod method;

    @NotBlank(message = "currency is required")
    private String currency;
    private String transactionId;
}
