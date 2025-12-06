package com.invoicemanagement.ingestion.domain;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: Invoice document received and uploaded to S3.
 * Triggers initial processing workflow.
 *
 * DDD Pattern: Domain Event
 * Bounded Context: InvoiceIngestionContext
 * Published To: Internal (triggers extraction workflow)
 */
@Getter
@ToString(callSuper = true)
public class InvoiceReceivedEvent extends BaseDomainEvent {

    private final DocumentReference documentReference;
    private final Instant receivedAt;

    public InvoiceReceivedEvent(
        TenantId tenantId,
        UUID invoiceId,
        DocumentReference documentReference,
        Instant receivedAt
    ) {
        super(tenantId, invoiceId);
        this.documentReference = documentReference;
        this.receivedAt = receivedAt;
    }

    @Override
    public String getAggregateType() {
        return "Invoice";
    }
}
