package com.invoicemanagement.payment.infrastructure.gateway;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO for payment gateway execution response.
 */
@Getter
@Setter
public class PaymentGatewayResponse {
    private String transactionId;
    private String status;  // "SUCCESS", "PENDING", "FAILED"
    private String message;
    private String gatewayReference;
    private String expectedSettlementDate;
}
