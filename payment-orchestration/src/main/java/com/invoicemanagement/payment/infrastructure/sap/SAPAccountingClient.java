package com.invoicemanagement.payment.infrastructure.sap;

import com.invoicemanagement.payment.domain.PaymentRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Client for SAP accounting system integration.
 * Posts payment documents to SAP FI module.
 * 
 * Anti-Corruption Layer: Translates domain models to SAP-specific formats.
 */
@Component
public class SAPAccountingClient {

    private static final Logger logger = LoggerFactory.getLogger(SAPAccountingClient.class);
    private static final DateTimeFormatter SAP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WebClient sapWebClient;

    public SAPAccountingClient(
        @Value("${sap.api.base-url}") String sapBaseUrl,
        @Value("${sap.api.username}") String username,
        @Value("${sap.api.password}") String password
    ) {
        this.sapWebClient = WebClient.builder()
            .baseUrl(sapBaseUrl)
            .defaultHeaders(headers -> {
                headers.setBasicAuth(username, password);
                headers.set("Content-Type", "application/json");
            })
            .build();
    }

    /**
     * Post payment document to SAP accounting.
     * Returns SAP document number for tracking.
     */
    public SAPAccountingDocumentResponse postPaymentDocument(
        PaymentRun paymentRun,
        String tenantId
    ) {
        SAPAccountingDocumentRequest request = buildAccountingDocument(paymentRun);

        try {
            return sapWebClient.post()
                .uri("/api/accounting/documents")
                .header("X-Tenant-ID", tenantId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SAPAccountingDocumentResponse.class)
                .doOnError(error -> logger.error(
                    "SAP posting failed for payment {}: {}",
                    paymentRun.getPaymentRunId(),
                    error.getMessage()
                ))
                .block();

        } catch (Exception e) {
            logger.error("SAP posting error for payment {}", paymentRun.getPaymentRunId(), e);
            throw new SAPIntegrationException(
                "Failed to post payment to SAP: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Reverse a previously posted document (compensating action).
     */
    public void reverseDocument(String documentNumber, String reason, String tenantId) {
        try {
            sapWebClient.post()
                .uri("/api/accounting/documents/{docNum}/reverse", documentNumber)
                .header("X-Tenant-ID", tenantId)
                .bodyValue(new ReverseDocumentRequest(reason))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(error -> logger.error(
                    "SAP reversal failed for document {}: {}",
                    documentNumber,
                    error.getMessage()
                ))
                .block();

            logger.info("Successfully reversed SAP document: {}", documentNumber);

        } catch (Exception e) {
            logger.error("SAP reversal error for document {}", documentNumber, e);
            throw new SAPIntegrationException(
                "Failed to reverse SAP document: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Build SAP-specific accounting document from payment run.
     * Anti-Corruption Layer logic.
     */
    private SAPAccountingDocumentRequest buildAccountingDocument(PaymentRun paymentRun) {
        SAPAccountingDocumentRequest request = new SAPAccountingDocumentRequest();
        
        request.setCompanyCode("1000");  // Could be tenant-specific
        request.setDocumentType("KZ");   // Vendor payment
        request.setPostingDate(LocalDate.now().format(SAP_DATE_FORMAT));
        request.setDocumentDate(paymentRun.getScheduledPaymentDate().format(SAP_DATE_FORMAT));
        request.setCurrency(paymentRun.getTotalAmount().getCurrencyCode());
        request.setReference(paymentRun.getInvoiceNumber());

        // Create line items: Credit bank account, Debit vendor payable
        SAPAccountingDocumentRequest.SAPLineItem[] lineItems = new SAPAccountingDocumentRequest.SAPLineItem[2];

        // Credit bank account
        SAPAccountingDocumentRequest.SAPLineItem bankLine = new SAPAccountingDocumentRequest.SAPLineItem();
        bankLine.setGlAccount("113100");  // Bank clearing account
        bankLine.setAmount(paymentRun.getTotalAmount().getAmount());
        bankLine.setDebitCredit("C");
        bankLine.setReferenceKey(paymentRun.getInvoiceNumber());
        lineItems[0] = bankLine;

        // Debit vendor payable
        SAPAccountingDocumentRequest.SAPLineItem vendorLine = new SAPAccountingDocumentRequest.SAPLineItem();
        vendorLine.setGlAccount("160000");  // Accounts payable
        vendorLine.setAmount(paymentRun.getTotalAmount().getAmount());
        vendorLine.setDebitCredit("D");
        vendorLine.setVendorNumber(paymentRun.getVendorId());
        vendorLine.setReferenceKey(paymentRun.getInvoiceNumber());
        lineItems[1] = vendorLine;

        request.setLineItems(lineItems);
        return request;
    }

    // Exception class
    public static class SAPIntegrationException extends RuntimeException {
        public SAPIntegrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // Reversal request DTO
    private record ReverseDocumentRequest(String reason) {}
}
