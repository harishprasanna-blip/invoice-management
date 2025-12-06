package com.invoicemanagement.exceptionhandling.domain.events;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.util.UUID;

/**
 * Domain event emitted when exception case breaches SLA.
 *
 * DDD Pattern: Domain Event
 * Bounded Context: ExceptionHandlingContext
 */
@Getter
@ToString(callSuper = true)
public class SLABreachedEvent extends BaseDomainEvent {

    private final UUID caseId;
    private final UUID sourceAggregateId;
    private final Duration breachDuration;

    public SLABreachedEvent(
        TenantId tenantId,
        UUID caseId,
        UUID sourceAggregateId,
        Duration breachDuration
    ) {
        super(tenantId, caseId);
        this.caseId = caseId;
        this.sourceAggregateId = sourceAggregateId;
        this.breachDuration = breachDuration;
    }

    @Override
    public String getAggregateType() {
        return "ExceptionCase";
    }
}
