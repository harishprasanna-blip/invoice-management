package com.invoicemanagement.validation.domain.events;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * Domain event emitted when validation process starts.
 * Triggers SAP integration to fetch PO and GR data.
 *
 * DDD Pattern: Domain Event
 * Bounded Context: ValidationContext
 */
@Getter
@ToString(callSuper = true)
public class ValidationStartedEvent extends BaseDomainEvent {

    private final UUID transactionId;
    private final UUID invoiceId;
    private final String poNumber;
    private final String grNumber;

    public ValidationStartedEvent(
        TenantId tenantId,
        UUID transactionId,
        UUID invoiceId,
        String poNumber,
        String grNumber
    ) {
        super(tenantId, transactionId);
        this.transactionId = transactionId;
        this.invoiceId = invoiceId;
        this.poNumber = poNumber;
        this.grNumber = grNumber;
    }

    @Override
    public String getAggregateType() {
        return "PayableTransaction";
    }
}
