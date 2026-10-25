package com.folio.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentRequest {
    @NotNull(message = "bookingId is required")
    private Long bookingId;

    @NotNull(message = "method is required")
    private PaymentMethod method;

    // Kept for API compatibility only: every booking is priced and charged in EUR, so
    // the server always records "EUR" whatever is sent here. There is deliberately no
    // transactionId field — only Stripe ever supplies one (see PaymentServiceImpl.pay).
    @NotBlank(message = "currency is required")
    private String currency;
}
