package com.invoicemanagement.ingestion.domain;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: Invoice extraction failed after max retry attempts.
 * Triggers exception handling workflow for resolution.
 *
 * DDD Pattern: Domain Event
 * Bounded Context: InvoiceIngestionContext
 * Published To: ExceptionHandlingContext (for AI-driven resolution)
 */
@Getter
@ToString(callSuper = true)
public class ExtractionFailedEvent extends BaseDomainEvent {

    private final String failureReason;
    private final int retryCount;
    private final Instant failedAt;

    public ExtractionFailedEvent(
        TenantId tenantId,
        UUID invoiceId,
        String failureReason,
        int retryCount,
        Instant failedAt
    ) {
        super(tenantId, invoiceId);
        this.failureReason = failureReason;
        this.retryCount = retryCount;
        this.failedAt = failedAt;
    }

    @Override
    public String getAggregateType() {
        return "Invoice";
    }
}
