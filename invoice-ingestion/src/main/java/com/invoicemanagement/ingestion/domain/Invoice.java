package com.invoicemanagement.ingestion.domain;

import com.invoicemanagement.sharedkernel.domain.AuditLog;
import com.invoicemanagement.sharedkernel.domain.Money;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.DomainEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Invoice Aggregate Root.
 * Represents an invoice in the system with full lifecycle from ingestion to extraction.
 *
 * Responsibilities:
 * - Manage invoice state and transitions
 * - Enforce business invariants
 * - Produce domain events for state changes
 * - Coordinate document extraction workflow
 *
 * DDD Pattern: Aggregate Root
 * Bounded Context: InvoiceIngestionContext
 *
 * Invariants:
 * 1. Invoice must have at least one line item
 * 2. Sum of line item totals must equal invoice total (within tolerance)
 * 3. Confidence score must be >= threshold to transition to EXTRACTED status
 * 4. Retry count cannot exceed max attempts
 * 5. TenantId is immutable once set
 */
@Slf4j
@Getter
public class Invoice {

    // Identity
    private final UUID invoiceId;
    private final TenantId tenantId;

    // Value Objects
    private VendorReference vendorReference;
    private InvoiceMetadata invoiceMetadata;
    private final List<LineItem> lineItems;
    private DocumentReference documentReference;

    // Entity (within aggregate boundary)
    private ExtractionResult extractionResult;

    // State
    private IngestionStatus ingestionStatus;
    private AuditLog auditLog;

    // Timestamps
    private final Instant createdAt;
    private Instant updatedAt;

    // Optimistic locking
    private long version;

    // Domain events (not persisted - published and cleared)
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // Constants
    private static final BigDecimal CONFIDENCE_THRESHOLD = BigDecimal.valueOf(0.85);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final BigDecimal TOTAL_TOLERANCE_PERCENTAGE = BigDecimal.valueOf(0.01); // 1%

    /**
     * Private constructor - use factory methods.
     */
    private Invoice(
        UUID invoiceId,
        TenantId tenantId,
        DocumentReference documentReference,
        VendorReference vendorReference
    ) {
        this.invoiceId = Objects.requireNonNull(invoiceId, "Invoice ID cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID cannot be null");
        this.documentReference = Objects.requireNonNull(documentReference, "Document reference cannot be null");
        this.vendorReference = vendorReference; // May be null initially (extracted later)
        this.lineItems = new ArrayList<>();
        this.ingestionStatus = IngestionStatus.RECEIVED;
        this.auditLog = AuditLog.empty();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0;
    }

    /**
     * Reconstitution constructor for loading from database.
     * Used by InvoiceMapper to rebuild aggregate from persistence.
     */
    private Invoice(
        UUID invoiceId,
        TenantId tenantId,
        VendorReference vendorReference,
        InvoiceMetadata invoiceMetadata,
        List<LineItem> lineItems,
        DocumentReference documentReference,
        ExtractionResult extractionResult,
        IngestionStatus ingestionStatus,
        AuditLog auditLog,
        Instant createdAt,
        Instant updatedAt,
        long version
    ) {
        this.invoiceId = invoiceId;
        this.tenantId = tenantId;
        this.vendorReference = vendorReference;
        this.invoiceMetadata = invoiceMetadata;
        this.lineItems = new ArrayList<>(lineItems);
        this.documentReference = documentReference;
        this.extractionResult = extractionResult;
        this.ingestionStatus = ingestionStatus;
        this.auditLog = auditLog;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /**
     * Factory method: Create new invoice from uploaded document.
     */
    public static Invoice create(
        TenantId tenantId,
        DocumentReference documentReference,
        VendorReference vendorHint
    ) {
        Invoice invoice = new Invoice(
            UUID.randomUUID(),
            tenantId,
            documentReference,
            vendorHint
        );

        // Record creation in audit log
        invoice.addAuditEntry(AuditLog.AuditAction.CREATED, null, "Invoice created from document upload");

        // Emit domain event
        invoice.addDomainEvent(new InvoiceReceivedEvent(
            tenantId,
            invoice.invoiceId,
            documentReference,
            Instant.now()
        ));

        log.info("Invoice created: invoiceId={}, tenantId={}, format={}",
            invoice.invoiceId, tenantId, documentReference.getDocumentFormat());

        return invoice;
    }

    /**
     * Reconstitute invoice from persistence (no events).
     */
    public static Invoice reconstitute(
        UUID invoiceId,
        TenantId tenantId,
        VendorReference vendorReference,
        InvoiceMetadata invoiceMetadata,
        List<LineItem> lineItems,
        DocumentReference documentReference,
        ExtractionResult extractionResult,
        IngestionStatus ingestionStatus,
        AuditLog auditLog,
        Instant createdAt,
        Instant updatedAt,
        long version
    ) {
        return new Invoice(
            invoiceId,
            tenantId,
            vendorReference,
            invoiceMetadata,
            lineItems,
            documentReference,
            extractionResult,
            ingestionStatus,
            auditLog,
            createdAt,
            updatedAt,
            version
        );
    }

    /**
     * Start extraction process.
     */
    public void startExtraction() {
        if (ingestionStatus != IngestionStatus.RECEIVED) {
            throw new IllegalStateException(
                String.format("Cannot start extraction from status: %s", ingestionStatus)
            );
        }

        this.ingestionStatus = IngestionStatus.EXTRACTING;
        this.updatedAt = Instant.now();

        addAuditEntry(AuditLog.AuditAction.UPDATED, null, "Extraction started");

        log.info("Extraction started for invoice: {}", invoiceId);
    }

    /**
     * Complete extraction with results from AI service.
     */
    public void completeExtraction(
        ExtractionResult extractionResult,
        InvoiceMetadata metadata,
        List<LineItem> extractedLineItems
    ) {
        if (ingestionStatus != IngestionStatus.EXTRACTING) {
            throw new IllegalStateException(
                String.format("Cannot complete extraction from status: %s", ingestionStatus)
            );
        }

        Objects.requireNonNull(extractionResult, "Extraction result cannot be null");
        Objects.requireNonNull(metadata, "Invoice metadata cannot be null");
        Objects.requireNonNull(extractedLineItems, "Line items cannot be null");

        // Validate extraction result
        if (!extractionResult.meetsConfidenceThreshold(CONFIDENCE_THRESHOLD)) {
            failExtraction(extractionResult, "Confidence score below threshold: " + extractionResult.getConfidenceScore());
            return;
        }

        if (extractedLineItems.isEmpty()) {
            failExtraction(extractionResult, "No line items extracted");
            return;
        }

        // Set extracted data
        this.extractionResult = extractionResult;
        this.invoiceMetadata = metadata;
        this.lineItems.clear();
        this.lineItems.addAll(extractedLineItems);

        // Validate business invariants
        try {
            validateLineItemTotals();
        } catch (IllegalArgumentException e) {
            failExtraction(extractionResult, "Line item validation failed: " + e.getMessage());
            return;
        }

        // Transition to EXTRACTED status
        this.ingestionStatus = IngestionStatus.EXTRACTED;
        this.updatedAt = Instant.now();

        addAuditEntry(AuditLog.AuditAction.EXTRACTED, null,
            String.format("Extraction completed with confidence: %s", extractionResult.getConfidenceScore()));

        // Emit domain event for downstream contexts
        addDomainEvent(new InvoiceExtractedEvent(
            tenantId,
            invoiceId,
            extractionResult.getExtractionId(),
            metadata,
            lineItems,
            extractionResult.getConfidenceScore(),
            Instant.now()
        ));

        log.info("Extraction completed for invoice: {}, confidence: {}",
            invoiceId, extractionResult.getConfidenceScore());
    }

    /**
     * Fail extraction with errors.
     */
    public void failExtraction(ExtractionResult extractionResult, String reason) {
        Objects.requireNonNull(extractionResult, "Extraction result cannot be null");

        this.extractionResult = extractionResult;

        // Check if should retry or permanently fail
        if (extractionResult.hasExceededMaxRetries(MAX_RETRY_ATTEMPTS)) {
            this.ingestionStatus = IngestionStatus.EXTRACTION_FAILED;
            this.updatedAt = Instant.now();

            addAuditEntry(AuditLog.AuditAction.UPDATED, null,
                String.format("Extraction failed permanently after %d retries: %s",
                    extractionResult.getRetryCount(), reason));

            // Emit failure event for exception handling context
            addDomainEvent(new ExtractionFailedEvent(
                tenantId,
                invoiceId,
                reason,
                extractionResult.getRetryCount(),
                Instant.now()
            ));

            log.error("Extraction failed permanently for invoice: {}, reason: {}", invoiceId, reason);
        } else {
            // Stay in EXTRACTING status for retry
            addAuditEntry(AuditLog.AuditAction.UPDATED, null,
                String.format("Extraction attempt %d failed: %s", extractionResult.getRetryCount(), reason));

            log.warn("Extraction attempt {} failed for invoice: {}, will retry",
                extractionResult.getRetryCount(), invoiceId);
        }
    }

    /**
     * Manually correct invoice metadata (human intervention).
     */
    public void correctMetadata(
        InvoiceMetadata correctedMetadata,
        UUID userId,
        String correctionReason
    ) {
        Objects.requireNonNull(correctedMetadata, "Corrected metadata cannot be null");
        Objects.requireNonNull(userId, "User ID cannot be null");

        InvoiceMetadata oldMetadata = this.invoiceMetadata;
        this.invoiceMetadata = correctedMetadata;
        this.updatedAt = Instant.now();

        // Record field changes in audit log
        var changes = new java.util.HashMap<String, AuditLog.FieldChange>();
        if (oldMetadata != null && !oldMetadata.getInvoiceNumber().equals(correctedMetadata.getInvoiceNumber())) {
            changes.put("invoiceNumber", AuditLog.FieldChange.of(
                "invoiceNumber",
                oldMetadata.getInvoiceNumber(),
                correctedMetadata.getInvoiceNumber(),
                correctionReason
            ));
        }

        addAuditEntry(AuditLog.AuditAction.UPDATED, userId, changes, "Manual metadata correction");

        // Emit metadata correction event
        addDomainEvent(new InvoiceMetadataCorrectedEvent(
            tenantId,
            invoiceId,
            oldMetadata,
            correctedMetadata,
            userId,
            Instant.now()
        ));

        log.info("Metadata corrected for invoice: {} by user: {}", invoiceId, userId);
    }

    /**
     * Validate line item totals match invoice total.
     * Invariant: Sum of line items must equal invoice total (within tolerance).
     */
    private void validateLineItemTotals() {
        if (lineItems.isEmpty()) {
            throw new IllegalArgumentException("Invoice must have at least one line item");
        }

        Money lineItemSum = lineItems.stream()
            .map(LineItem::getTotalAmount)
            .reduce(Money.zero(invoiceMetadata.getCurrency()), Money::add);

        Money difference = lineItemSum.subtract(invoiceMetadata.getTotalAmount()).abs();
        Money tolerance = invoiceMetadata.getTotalAmount().percentage(TOTAL_TOLERANCE_PERCENTAGE);

        if (difference.isGreaterThan(tolerance)) {
            throw new IllegalArgumentException(
                String.format("Line item total (%s) does not match invoice total (%s), difference: %s, tolerance: %s",
                    lineItemSum, invoiceMetadata.getTotalAmount(), difference, tolerance)
            );
        }
    }

    /**
     * Add audit entry (without field changes).
     */
    private void addAuditEntry(AuditLog.AuditAction action, UUID userId, String reason) {
        addAuditEntry(action, userId, Collections.emptyMap(), reason);
    }

    /**
     * Add audit entry with field changes.
     */
    private void addAuditEntry(
        AuditLog.AuditAction action,
        UUID userId,
        java.util.Map<String, AuditLog.FieldChange> changes,
        String reason
    ) {
        AuditLog.AuditEntry entry = AuditLog.AuditEntry.builder()
            .timestamp(Instant.now())
            .action(action)
            .userId(userId)
            .changes(changes)
            .tenantId(tenantId)
            .build();

        this.auditLog = this.auditLog.addEntry(entry);
    }

    /**
     * Add domain event to be published.
     */
    private void addDomainEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }

    /**
     * Get and clear domain events (for publishing).
     */
    public List<DomainEvent> getDomainEventsAndClear() {
        List<DomainEvent> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return events;
    }

    /**
     * Check if invoice has pending domain events.
     */
    public boolean hasDomainEvents() {
        return !domainEvents.isEmpty();
    }

    /**
     * Get immutable copy of line items.
     */
    public List<LineItem> getLineItems() {
        return Collections.unmodifiableList(lineItems);
    }

    /**
     * Ingestion status enumeration.
     */
    public enum IngestionStatus {
        RECEIVED,           // Document received, awaiting extraction
        EXTRACTING,         // AI extraction in progress
        EXTRACTED,          // Extraction completed successfully
        EXTRACTION_FAILED   // Extraction failed after max retries
    }
}
