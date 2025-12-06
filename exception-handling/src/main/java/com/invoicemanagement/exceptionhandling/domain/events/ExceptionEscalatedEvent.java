package com.invoicemanagement.exceptionhandling.domain.events;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import com.invoicemanagement.exceptionhandling.domain.ExceptionCase;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * Domain event emitted when exception case is escalated.
 *
 * DDD Pattern: Domain Event
 * Bounded Context: ExceptionHandlingContext
 */
@Getter
@ToString(callSuper = true)
public class ExceptionEscalatedEvent extends BaseDomainEvent {

    private final UUID caseId;
    private final UUID sourceAggregateId;
    private final ExceptionCase.EscalationLevel newLevel;
    private final String reason;

    public ExceptionEscalatedEvent(
        TenantId tenantId,
        UUID caseId,
        UUID sourceAggregateId,
        ExceptionCase.EscalationLevel newLevel,
        String reason
    ) {
        super(tenantId, caseId);
        this.caseId = caseId;
        this.sourceAggregateId = sourceAggregateId;
        this.newLevel = newLevel;
        this.reason = reason;
    }

    @Override
    public String getAggregateType() {
        return "ExceptionCase";
    }
}
