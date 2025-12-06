package com.invoicemanagement.ingestion.domain;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event: Invoice successfully extracted from document with AI.
 * Triggers validation workflow in ValidationContext.
 *
 * DDD Pattern: Domain Event
 * Bounded Context: InvoiceIngestionContext
 * Published To: ValidationContext (for 2/3-way matching)
 */
@Getter
@ToString(callSuper = true)
public class InvoiceExtractedEvent extends BaseDomainEvent {

    private final UUID extractionId;
    private final InvoiceMetadata invoiceMetadata;
    private final List<LineItem> lineItems;
    private final BigDecimal confidenceScore;
    private final Instant extractedAt;

    public InvoiceExtractedEvent(
        TenantId tenantId,
        UUID invoiceId,
        UUID extractionId,
        InvoiceMetadata invoiceMetadata,
        List<LineItem> lineItems,
        BigDecimal confidenceScore,
        Instant extractedAt
    ) {
        super(tenantId, invoiceId);
        this.extractionId = extractionId;
        this.invoiceMetadata = invoiceMetadata;
        this.lineItems = Collections.unmodifiableList(lineItems);
        this.confidenceScore = confidenceScore;
        this.extractedAt = extractedAt;
    }

    @Override
    public String getAggregateType() {
        return "Invoice";
    }

    /**
     * Get invoice number for easy reference.
     */
    public String getInvoiceNumber() {
        return invoiceMetadata.getInvoiceNumber();
    }

    /**
     * Get total amount for easy reference.
     */
    public String getTotalAmount() {
        return invoiceMetadata.getTotalAmount().toFormattedString();
    }
}
