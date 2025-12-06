package com.invoicemanagement.payment.presentation;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO for approving a payment request.
 */
@Getter
@Setter
public class ApprovePaymentRequest {
    private String approver;
    private String notes;
}
