package com.invoicemanagement.ingestion.domain;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: Invoice metadata manually corrected by user.
 * Records human intervention for audit trail.
 *
 * DDD Pattern: Domain Event
 * Bounded Context: InvoiceIngestionContext
 * Published To: Internal (audit/analytics), possibly re-triggers validation
 */
@Getter
@ToString(callSuper = true)
public class InvoiceMetadataCorrectedEvent extends BaseDomainEvent {

    private final InvoiceMetadata oldMetadata;
    private final InvoiceMetadata newMetadata;
    private final UUID correctedBy;
    private final Instant correctedAt;

    public InvoiceMetadataCorrectedEvent(
        TenantId tenantId,
        UUID invoiceId,
        InvoiceMetadata oldMetadata,
        InvoiceMetadata newMetadata,
        UUID correctedBy,
        Instant correctedAt
    ) {
        super(tenantId, invoiceId);
        this.oldMetadata = oldMetadata;
        this.newMetadata = newMetadata;
        this.correctedBy = correctedBy;
        this.correctedAt = correctedAt;
    }

    @Override
    public String getAggregateType() {
        return "Invoice";
    }
}
