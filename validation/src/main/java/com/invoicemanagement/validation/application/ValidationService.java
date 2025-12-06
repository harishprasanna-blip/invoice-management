package com.invoicemanagement.validation.application;

import com.invoicemanagement.sharedkernel.domain.Money;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.tenant.TenantContext;
import com.invoicemanagement.validation.domain.*;
import com.invoicemanagement.validation.infrastructure.sap.SAPProcurementAdapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service coordinating validation workflows.
 * Orchestrates domain services and infrastructure adapters.
 *
 * DDD Pattern: Application Service
 * Bounded Context: ValidationContext
 */
@Service
public class ValidationService {

    private final PayableTransactionRepository transactionRepository;
    private final ThreeWayMatchingService matchingService;
    private final ComplianceValidationService complianceService;
    private final SAPProcurementAdapter sapAdapter;

    public ValidationService(
        PayableTransactionRepository transactionRepository,
        ThreeWayMatchingService matchingService,
        ComplianceValidationService complianceService,
        SAPProcurementAdapter sapAdapter
    ) {
        this.transactionRepository = transactionRepository;
        this.matchingService = matchingService;
        this.complianceService = complianceService;
        this.sapAdapter = sapAdapter;
    }

    /**
     * Start validation process for an invoice.
     * Triggered by InvoiceExtractedEvent.
     *
     * @param invoiceId Invoice identifier
     * @param invoiceNumber Invoice number
     * @param totalAmount Invoice total amount
     * @param poNumber Purchase Order number (optional)
     * @param grNumber Goods Receipt number (optional)
     * @return Transaction ID
     */
    @Transactional
    public UUID startValidation(
        UUID invoiceId,
        String invoiceNumber,
        Money totalAmount,
        String poNumber,
        String grNumber
    ) {
        TenantId tenantId = TenantContext.getCurrentTenant();

        // Check if validation already exists for this invoice
        if (transactionRepository.existsByInvoiceId(invoiceId)) {
            throw new IllegalStateException("Validation already exists for invoice: " + invoiceId);
        }

        // Create invoice reference
        PayableTransaction.InvoiceReference invoiceReference =
            PayableTransaction.InvoiceReference.of(invoiceId, invoiceNumber, totalAmount);

        // Fetch PO from SAP (if provided)
        PurchaseOrderReference poReference = null;
        if (poNumber != null && !poNumber.isBlank()) {
            Optional<PurchaseOrderReference> poOpt = sapAdapter.fetchPurchaseOrder(
                poNumber,
                tenantId.getId().toString()
            );

            if (poOpt.isEmpty()) {
                throw new IllegalStateException("Purchase Order not found in SAP: " + poNumber);
            }

            poReference = poOpt.get();

            // Validate PO is open for matching
            if (!poReference.isOpenForMatching()) {
                throw new IllegalStateException("Purchase Order is not open for matching: " + poNumber);
            }
        }

        // Fetch GR from SAP (if provided)
        GoodsReceiptReference grReference = null;
        if (grNumber != null && !grNumber.isBlank()) {
            Optional<GoodsReceiptReference> grOpt = sapAdapter.fetchGoodsReceipt(
                grNumber,
                tenantId.getId().toString()
            );

            if (grOpt.isEmpty()) {
                throw new IllegalStateException("Goods Receipt not found in SAP: " + grNumber);
            }

            grReference = grOpt.get();
        } else if (poReference != null) {
            // No GR number provided, try to fetch latest GR for PO
            Optional<GoodsReceiptReference> grOpt = sapAdapter.fetchGoodsReceiptByPO(
                poNumber,
                tenantId.getId().toString()
            );
            grReference = grOpt.orElse(null);
        }

        // Create payable transaction
        PayableTransaction transaction = PayableTransaction.create(
            tenantId,
            invoiceReference,
            poReference,
            grReference
        );

        // Save (publishes ValidationStartedEvent)
        transaction = transactionRepository.save(transaction);

        // Immediately perform matching and compliance checks
        performMatchingAndCompliance(transaction);

        return transaction.getTransactionId();
    }

    /**
     * Perform matching and compliance validation.
     */
    @Transactional
    public void performMatchingAndCompliance(PayableTransaction transaction) {
        // Perform matching
        MatchingResult matchingResult;

        if (transaction.getGoodsReceiptReference() != null) {
            // 3-way matching
            matchingResult = matchingService.performThreeWayMatch(
                transaction.getInvoiceReference(),
                transaction.getPurchaseOrderReference(),
                transaction.getGoodsReceiptReference()
            );
        } else if (transaction.getPurchaseOrderReference() != null) {
            // 2-way matching
            matchingResult = matchingService.performTwoWayMatch(
                transaction.getInvoiceReference(),
                transaction.getPurchaseOrderReference()
            );
        } else {
            // No PO - create default matching result
            matchingResult = MatchingResult.create(
                MatchingResult.MatchType.NO_PO,
                MatchingResult.MatchStatus.MATCHED,
                List.of(),
                List.of(),
                java.math.BigDecimal.valueOf(100)
            );
        }

        transaction.completeMatching(matchingResult);

        // Perform compliance checks
        // In production: Get tenant country from tenant configuration
        String tenantCountryCode = "US"; // Simplified
        List<ComplianceCheck> complianceChecks = complianceService.performComplianceChecks(
            transaction,
            tenantCountryCode
        );

        for (ComplianceCheck check : complianceChecks) {
            transaction.addComplianceCheck(check);
        }

        // Complete validation
        transaction.completeValidation();

        // Save (publishes ValidationPassed, MismatchDetected, or ComplianceViolationDetected events)
        transactionRepository.save(transaction);
    }

    /**
     * Get validation status for invoice.
     */
    @Transactional(readOnly = true)
    public Optional<ValidationStatusDTO> getValidationStatus(UUID invoiceId) {
        return transactionRepository.findByInvoiceId(invoiceId)
            .map(this::toStatusDTO);
    }

    /**
     * Get transaction by ID.
     */
    @Transactional(readOnly = true)
    public Optional<PayableTransaction> getTransaction(UUID transactionId) {
        return transactionRepository.findById(transactionId);
    }

    /**
     * Re-validate transaction after exception resolution.
     * Used by ExceptionHandlingContext after manual correction.
     */
    @Transactional
    public void revalidate(UUID transactionId) {
        PayableTransaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        // Re-perform matching and compliance
        performMatchingAndCompliance(transaction);
    }

    private ValidationStatusDTO toStatusDTO(PayableTransaction transaction) {
        return new ValidationStatusDTO(
            transaction.getTransactionId(),
            transaction.getInvoiceReference().getInvoiceId(),
            transaction.getValidationStatus(),
            transaction.getMatchingResult() != null
                ? transaction.getMatchingResult().getMatchStatus()
                : null,
            transaction.getVariances(),
            transaction.getFailedComplianceChecks(),
            transaction.isReadyForPayment(),
            transaction.getValidatedAt()
        );
    }

    /**
     * DTO for validation status response.
     */
    public record ValidationStatusDTO(
        UUID transactionId,
        UUID invoiceId,
        PayableTransaction.ValidationStatus validationStatus,
        MatchingResult.MatchStatus matchStatus,
        List<MatchingResult.Variance> variances,
        List<ComplianceCheck> failedComplianceChecks,
        boolean readyForPayment,
        java.time.Instant validatedAt
    ) {}
}
