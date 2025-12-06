package com.invoicemanagement.validation.domain;

import com.invoicemanagement.sharedkernel.domain.AuditLog;
import com.invoicemanagement.sharedkernel.domain.AuditLogHelper;
import com.invoicemanagement.sharedkernel.domain.Money;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.DomainEvent;
import com.invoicemanagement.validation.domain.events.ComplianceViolationDetectedEvent;
import com.invoicemanagement.validation.domain.events.MismatchDetectedEvent;
import com.invoicemanagement.validation.domain.events.ValidationPassedEvent;
import com.invoicemanagement.validation.domain.events.ValidationStartedEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root representing a payable transaction in validation.
 * Coordinates 2-way/3-way matching between Invoice, PO, and GR.
 * Performs compliance validation (GDPR, tax, etc.).
 *
 * DDD Pattern: Aggregate Root
 * Bounded Context: ValidationContext
 * Consistency Boundary: All matching and compliance checks
 */
@Getter
@EqualsAndHashCode(of = "transactionId")
@ToString
public class PayableTransaction implements Serializable {

    // Identity
    private final UUID transactionId;
    private final TenantId tenantId;

    // References to other aggregates/contexts
    private final InvoiceReference invoiceReference;
    private PurchaseOrderReference purchaseOrderReference;
    private GoodsReceiptReference goodsReceiptReference;

    // Matching results (entity within aggregate)
    private MatchingResult matchingResult;

    // Compliance checks (entities within aggregate)
    private final List<ComplianceCheck> complianceChecks;

    // Status
    private ValidationStatus validationStatus;
    private Instant validatedAt;

    // Audit trail (immutable value object - reassign on update)
    private AuditLog auditLog;

    // Domain events (not persisted)
    private transient List<DomainEvent> domainEvents = new ArrayList<>();

    private PayableTransaction(
        UUID transactionId,
        TenantId tenantId,
        InvoiceReference invoiceReference,
        PurchaseOrderReference purchaseOrderReference,
        GoodsReceiptReference goodsReceiptReference,
        MatchingResult matchingResult,
        List<ComplianceCheck> complianceChecks,
        ValidationStatus validationStatus,
        Instant validatedAt,
        AuditLog auditLog
    ) {
        this.transactionId = Objects.requireNonNull(transactionId, "Transaction ID cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID cannot be null");
        this.invoiceReference = Objects.requireNonNull(invoiceReference, "Invoice reference cannot be null");
        this.purchaseOrderReference = purchaseOrderReference;
        this.goodsReceiptReference = goodsReceiptReference;
        this.matchingResult = matchingResult;
        this.complianceChecks = new ArrayList<>(complianceChecks);
        this.validationStatus = Objects.requireNonNull(validationStatus, "Validation status cannot be null");
        this.validatedAt = validatedAt;
        this.auditLog = Objects.requireNonNull(auditLog, "Audit log cannot be null");
    }

    /**
     * Factory method for creating new payable transaction for validation.
     * Emits ValidationStartedEvent.
     */
    public static PayableTransaction create(
        TenantId tenantId,
        InvoiceReference invoiceReference,
        PurchaseOrderReference purchaseOrderReference,
        GoodsReceiptReference goodsReceiptReference
    ) {
        UUID transactionId = UUID.randomUUID();

        AuditLog auditLog = AuditLogHelper.addEntry(
            AuditLog.empty(),
            "TRANSACTION_CREATED",
            null,
            null,
            null,
            tenantId
        );

        PayableTransaction transaction = new PayableTransaction(
            transactionId,
            tenantId,
            invoiceReference,
            purchaseOrderReference,
            goodsReceiptReference,
            null, // No matching result yet
            Collections.emptyList(), // No compliance checks yet
            ValidationStatus.PENDING,
            null,
            auditLog
        );

        transaction.addDomainEvent(new ValidationStartedEvent(
            tenantId,
            transactionId,
            invoiceReference.getInvoiceId(),
            purchaseOrderReference != null ? purchaseOrderReference.getPoNumber() : null,
            goodsReceiptReference != null ? goodsReceiptReference.getGrNumber() : null
        ));

        return transaction;
    }

    /**
     * Factory method for reconstituting from persistence.
     * Does NOT emit domain events.
     */
    public static PayableTransaction reconstitute(
        UUID transactionId,
        TenantId tenantId,
        InvoiceReference invoiceReference,
        PurchaseOrderReference purchaseOrderReference,
        GoodsReceiptReference goodsReceiptReference,
        MatchingResult matchingResult,
        List<ComplianceCheck> complianceChecks,
        ValidationStatus validationStatus,
        Instant validatedAt,
        AuditLog auditLog
    ) {
        return new PayableTransaction(
            transactionId,
            tenantId,
            invoiceReference,
            purchaseOrderReference,
            goodsReceiptReference,
            matchingResult,
            complianceChecks,
            validationStatus,
            validatedAt,
            auditLog
        );
    }

    /**
     * Complete matching process with result.
     * Business logic: Determines if validation passed or mismatch detected.
     */
    public void completeMatching(MatchingResult matchingResult) {
        if (this.validationStatus == ValidationStatus.COMPLETED) {
            throw new IllegalStateException("Transaction already completed");
        }

        this.matchingResult = Objects.requireNonNull(matchingResult, "Matching result cannot be null");
        this.validationStatus = ValidationStatus.MATCHING_COMPLETED;

        this.auditLog = AuditLogHelper.addEntry(
            this.auditLog,
            "MATCHING_COMPLETED",
            null,
            "matchStatus",
            matchingResult.getMatchStatus().name(),
            this.tenantId
        );

        // Emit event based on match result
        if (matchingResult.isPassed() && !matchingResult.hasVariances()) {
            // Perfect match - no variances
            addDomainEvent(new ValidationPassedEvent(
                tenantId,
                transactionId,
                invoiceReference.getInvoiceId(),
                matchingResult.getMatchType(),
                matchingResult.getOverallScore()
            ));
        } else if (matchingResult.getMatchStatus() == MatchingResult.MatchStatus.MISMATCH) {
            // Mismatch detected - escalate to ExceptionHandlingContext
            addDomainEvent(new MismatchDetectedEvent(
                tenantId,
                transactionId,
                invoiceReference.getInvoiceId(),
                matchingResult.getVariancesExceedingTolerance(),
                matchingResult.getOverallScore()
            ));
        }
    }

    /**
     * Add compliance check result.
     * If violation detected, emits ComplianceViolationDetectedEvent.
     */
    public void addComplianceCheck(ComplianceCheck complianceCheck) {
        Objects.requireNonNull(complianceCheck, "Compliance check cannot be null");

        this.complianceChecks.add(complianceCheck);

        this.auditLog = AuditLogHelper.addEntry(
            this.auditLog,
            "COMPLIANCE_CHECK_ADDED",
            null,
            complianceCheck.getCheckType().name(),
            complianceCheck.isPassed() ? "PASSED" : "FAILED",
            this.tenantId
        );

        // If compliance check failed, emit violation event
        if (!complianceCheck.isPassed()) {
            addDomainEvent(new ComplianceViolationDetectedEvent(
                tenantId,
                transactionId,
                invoiceReference.getInvoiceId(),
                complianceCheck.getCheckType(),
                complianceCheck.getViolationDetails()
            ));
        }
    }

    /**
     * Complete validation process.
     * Business rule: All compliance checks must pass, and matching must be acceptable.
     */
    public void completeValidation() {
        if (this.validationStatus == ValidationStatus.COMPLETED) {
            throw new IllegalStateException("Transaction already completed");
        }

        if (this.matchingResult == null) {
            throw new IllegalStateException("Cannot complete validation without matching result");
        }

        // Check if all compliance checks passed
        boolean allComplianceChecksPassed = complianceChecks.stream()
            .allMatch(ComplianceCheck::isPassed);

        if (!allComplianceChecksPassed) {
            this.validationStatus = ValidationStatus.FAILED_COMPLIANCE;
            this.validatedAt = Instant.now();
            this.auditLog = AuditLogHelper.addEntry(
                this.auditLog,
                "VALIDATION_FAILED",
                null,
                "reason",
                "Compliance checks failed",
                this.tenantId
            );
            return;
        }

        // Check if matching passed
        if (matchingResult.getMatchStatus() == MatchingResult.MatchStatus.MISMATCH) {
            this.validationStatus = ValidationStatus.FAILED_MATCHING;
            this.validatedAt = Instant.now();
            this.auditLog = AuditLogHelper.addEntry(
                this.auditLog,
                "VALIDATION_FAILED",
                null,
                "reason",
                "Matching failed",
                this.tenantId
            );
            return;
        }

        // All checks passed
        this.validationStatus = ValidationStatus.COMPLETED;
        this.validatedAt = Instant.now();

        this.auditLog = AuditLogHelper.addEntry(
            this.auditLog,
            "VALIDATION_COMPLETED",
            null,
            null,
            null,
            this.tenantId
        );

        // Emit ValidationPassedEvent if not already emitted during matching
        if (matchingResult.isPassed() && allComplianceChecksPassed) {
            addDomainEvent(new ValidationPassedEvent(
                tenantId,
                transactionId,
                invoiceReference.getInvoiceId(),
                matchingResult.getMatchType(),
                matchingResult.getOverallScore()
            ));
        }
    }

    /**
     * Check if transaction is ready for payment.
     */
    public boolean isReadyForPayment() {
        return validationStatus == ValidationStatus.COMPLETED
            && matchingResult != null
            && matchingResult.isPassed()
            && complianceChecks.stream().allMatch(ComplianceCheck::isPassed);
    }

    /**
     * Get all variances from matching result.
     */
    public List<MatchingResult.Variance> getVariances() {
        return matchingResult != null
            ? matchingResult.getVariances()
            : Collections.emptyList();
    }

    /**
     * Get variances exceeding tolerance.
     */
    public List<MatchingResult.Variance> getVariancesExceedingTolerance() {
        return matchingResult != null
            ? matchingResult.getVariancesExceedingTolerance()
            : Collections.emptyList();
    }

    /**
     * Get failed compliance checks.
     */
    public List<ComplianceCheck> getFailedComplianceChecks() {
        return complianceChecks.stream()
            .filter(check -> !check.isPassed())
            .toList();
    }

    /**
     * Get immutable copy of compliance checks.
     */
    public List<ComplianceCheck> getComplianceChecks() {
        return Collections.unmodifiableList(complianceChecks);
    }

    // Domain event management
    protected void addDomainEvent(DomainEvent event) {
        if (domainEvents == null) {
            domainEvents = new ArrayList<>();
        }
        domainEvents.add(event);
    }

    public List<DomainEvent> getDomainEventsAndClear() {
        List<DomainEvent> events = new ArrayList<>(domainEvents != null ? domainEvents : Collections.emptyList());
        if (domainEvents != null) {
            domainEvents.clear();
        }
        return events;
    }

    /**
     * Validation status enumeration.
     */
    public enum ValidationStatus {
        PENDING,                // Validation not started
        MATCHING_COMPLETED,     // Matching completed, compliance pending
        COMPLETED,              // All validations passed
        FAILED_MATCHING,        // Matching failed (variances exceed tolerance)
        FAILED_COMPLIANCE       // Compliance checks failed
    }

    /**
     * Invoice reference value object.
     */
    @Getter
    @EqualsAndHashCode
    @ToString
    public static class InvoiceReference implements Serializable {
        private final UUID invoiceId;
        private final String invoiceNumber;
        private final Money totalAmount;
        private final String currency;

        public InvoiceReference(
            UUID invoiceId,
            String invoiceNumber,
            Money totalAmount,
            String currency
        ) {
            this.invoiceId = Objects.requireNonNull(invoiceId, "Invoice ID cannot be null");
            this.invoiceNumber = Objects.requireNonNull(invoiceNumber, "Invoice number cannot be null");
            this.totalAmount = Objects.requireNonNull(totalAmount, "Total amount cannot be null");
            this.currency = Objects.requireNonNull(currency, "Currency cannot be null");
        }

        public static InvoiceReference of(
            UUID invoiceId,
            String invoiceNumber,
            Money totalAmount
        ) {
            return new InvoiceReference(
                invoiceId,
                invoiceNumber,
                totalAmount,
                totalAmount.getCurrencyCode()
            );
        }
    }
}
