package com.folio.payment;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private Long id;
    private Long bookingId;
    private BigDecimal amount;
    private PaymentMethod method;
    private String currency;
    private String transactionId;
    private String refundTransactionId;
    private BigDecimal refundedAmount;
    private PaymentStatus status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
