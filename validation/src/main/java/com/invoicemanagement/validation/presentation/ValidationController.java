package com.invoicemanagement.validation.presentation;

import com.invoicemanagement.validation.application.ValidationService;
import com.invoicemanagement.validation.domain.PayableTransaction;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API controller for validation operations.
 * Presentation Layer.
 */
@RestController
@RequestMapping("/api/v1/validation")
public class ValidationController {

    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    /**
     * Get validation status for invoice.
     * GET /api/v1/validation/invoices/{invoiceId}
     */
    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<ValidationService.ValidationStatusDTO> getValidationStatus(
        @PathVariable UUID invoiceId
    ) {
        return validationService.getValidationStatus(invoiceId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get transaction details.
     * GET /api/v1/validation/transactions/{transactionId}
     */
    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<TransactionDetailsResponse> getTransaction(
        @PathVariable UUID transactionId
    ) {
        return validationService.getTransaction(transactionId)
            .map(this::toDetailsResponse)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Re-validate transaction after exception resolution.
     * POST /api/v1/validation/transactions/{transactionId}/revalidate
     */
    @PostMapping("/transactions/{transactionId}/revalidate")
    public ResponseEntity<Void> revalidateTransaction(
        @PathVariable UUID transactionId
    ) {
        validationService.revalidate(transactionId);
        return ResponseEntity.accepted().build();
    }

    /**
     * Health check endpoint.
     * GET /api/v1/validation/health
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("ValidationContext", "UP"));
    }

    private TransactionDetailsResponse toDetailsResponse(PayableTransaction transaction) {
        return new TransactionDetailsResponse(
            transaction.getTransactionId(),
            transaction.getInvoiceReference().getInvoiceId(),
            transaction.getInvoiceReference().getInvoiceNumber(),
            transaction.getValidationStatus(),
            transaction.getMatchingResult() != null
                ? new MatchingDetails(
                    transaction.getMatchingResult().getMatchType(),
                    transaction.getMatchingResult().getMatchStatus(),
                    transaction.getMatchingResult().getOverallScore(),
                    transaction.getMatchingResult().getVariances().size()
                )
                : null,
            transaction.getComplianceChecks().size(),
            transaction.getFailedComplianceChecks().size(),
            transaction.isReadyForPayment(),
            transaction.getValidatedAt()
        );
    }

    record HealthResponse(String service, String status) {}

    record TransactionDetailsResponse(
        UUID transactionId,
        UUID invoiceId,
        String invoiceNumber,
        PayableTransaction.ValidationStatus validationStatus,
        MatchingDetails matchingDetails,
        int totalComplianceChecks,
        int failedComplianceChecks,
        boolean readyForPayment,
        java.time.Instant validatedAt
    ) {}

    record MatchingDetails(
        com.invoicemanagement.validation.domain.MatchingResult.MatchType matchType,
        com.invoicemanagement.validation.domain.MatchingResult.MatchStatus matchStatus,
        java.math.BigDecimal overallScore,
        int varianceCount
    ) {}
}
