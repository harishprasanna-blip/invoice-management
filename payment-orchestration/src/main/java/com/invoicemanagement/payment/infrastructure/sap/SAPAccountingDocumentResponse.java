package com.invoicemanagement.payment.infrastructure.sap;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO for SAP accounting document posting response.
 */
@Getter
@Setter
public class SAPAccountingDocumentResponse {
    private String documentNumber;
    private String fiscalYear;
    private String status;
    private String message;
}
