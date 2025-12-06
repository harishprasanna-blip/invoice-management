package com.invoicemanagement.payment.presentation;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for scheduling a payment request.
 */
@Getter
@Setter
public class SchedulePaymentRequest {
    private String invoiceId;
    private String invoiceNumber;
    private BigDecimal amount;
    private String currency;
    private String vendorId;
    private String vendorBankAccount;
    private LocalDate dueDate;
    private String paymentMethod;  // ACH, WIRE, CHECK, VIRTUAL_CARD
}
