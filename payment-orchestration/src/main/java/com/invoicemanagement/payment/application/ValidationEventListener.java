package com.invoicemanagement.payment.application;

import com.invoicemanagement.payment.domain.PaymentRun;
import com.invoicemanagement.sharedkernel.domain.Money;
import com.invoicemanagement.sharedkernel.events.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Event listener for ValidationPassedEvent from ValidationContext.
 * Triggers payment scheduling when validation completes successfully.
 * 
 * DDD Pattern: Event Choreography
 * Architecture Pattern: Event-Driven Integration
 */
@Component
public class ValidationEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ValidationEventListener.class);

    private final PaymentOrchestrationService paymentService;

    public ValidationEventListener(PaymentOrchestrationService paymentService) {
        this.paymentService = paymentService;
    }

    public void onEvent(DomainEvent event) {
        if ("ValidationPassed".equals(event.getEventType())) {
            handleValidationPassed(event);
        }
    }

    private void handleValidationPassed(DomainEvent event) {
        try {
            // Extract data from event (would use proper event class in production)
            UUID invoiceId = extractInvoiceId(event);
            String invoiceNumber = extractInvoiceNumber(event);
            Money totalAmount = extractTotalAmount(event);
            String vendorId = extractVendorId(event);
            String vendorBankAccount = extractVendorBankAccount(event);
            LocalDate dueDate = extractDueDate(event);

            // Schedule payment with default method (ACH)
            UUID paymentRunId = paymentService.schedulePayment(
                invoiceId,
                invoiceNumber,
                totalAmount,
                vendorId,
                vendorBankAccount,
                dueDate,
                PaymentRun.PaymentMethod.ACH
            );

            logger.info("Payment scheduled {} for validated invoice {}", paymentRunId, invoiceId);

        } catch (Exception e) {
            logger.error("Failed to schedule payment for validation event: {}", event.getEventId(), e);
            // In production: publish compensation event or create exception case
        }
    }

    // Helper methods to extract data from event
    // In production, these would use proper event DTOs
    private UUID extractInvoiceId(DomainEvent event) {
        // Mock extraction - in production would deserialize event payload
        return UUID.randomUUID();
    }

    private String extractInvoiceNumber(DomainEvent event) {
        return "INV-" + System.currentTimeMillis();
    }

    private Money extractTotalAmount(DomainEvent event) {
        return Money.of(BigDecimal.valueOf(1000.00), "USD");
    }

    private String extractVendorId(DomainEvent event) {
        return "VENDOR-001";
    }

    private String extractVendorBankAccount(DomainEvent event) {
        return "1234567890";
    }

    private LocalDate extractDueDate(DomainEvent event) {
        return LocalDate.now().plusDays(30);
    }

    public String getSubscribedEventType() {
        return "ValidationPassed";
    }
}
