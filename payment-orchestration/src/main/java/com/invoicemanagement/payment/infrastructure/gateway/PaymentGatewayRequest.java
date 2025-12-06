package com.invoicemanagement.payment.infrastructure.gateway;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO for payment gateway execution request.
 */
@Getter
@Setter
public class PaymentGatewayRequest {
    private String paymentMethod;  // "ACH", "WIRE", "CHECK", "VIRTUAL_CARD"
    private BigDecimal amount;
    private String currency;
    
    // Beneficiary details
    private String beneficiaryName;
    private String beneficiaryAccount;
    private String beneficiaryBank;
    private String beneficiaryRoutingNumber;
    
    // Remittance info
    private String invoiceNumber;
    private String reference;
    private String description;
    
    // Metadata
    private String idempotencyKey;
}
