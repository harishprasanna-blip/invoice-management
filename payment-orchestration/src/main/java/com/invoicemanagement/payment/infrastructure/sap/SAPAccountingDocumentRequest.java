package com.invoicemanagement.payment.infrastructure.sap;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO for SAP accounting document posting request.
 */
@Getter
@Setter
public class SAPAccountingDocumentRequest {
    private String companyCode;
    private String documentType;
    private String postingDate;
    private String documentDate;
    private String currency;
    private String reference;
    
    // Line items
    private SAPLineItem[] lineItems;

    @Getter
    @Setter
    public static class SAPLineItem {
        private String glAccount;
        private BigDecimal amount;
        private String debitCredit;  // "D" or "C"
        private String costCenter;
        private String vendorNumber;
        private String referenceKey;
    }
}
