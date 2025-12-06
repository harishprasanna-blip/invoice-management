package com.invoicemanagement.exceptionhandling.domain.events;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import com.invoicemanagement.exceptionhandling.domain.ExceptionCase;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * Domain event emitted when exception case is created.
 *
 * DDD Pattern: Domain Event
 * Bounded Context: ExceptionHandlingContext
 */
@Getter
@ToString(callSuper = true)
public class ExceptionCreatedEvent extends BaseDomainEvent {

    private final UUID caseId;
    private final ExceptionCase.ExceptionType exceptionType;
    private final ExceptionCase.ExceptionSeverity severity;
    private final UUID sourceAggregateId;
    private final String sourceContext;

    public ExceptionCreatedEvent(
        TenantId tenantId,
        UUID caseId,
        ExceptionCase.ExceptionType exceptionType,
        ExceptionCase.ExceptionSeverity severity,
        UUID sourceAggregateId,
        String sourceContext
    ) {
        super(tenantId, caseId);
        this.caseId = caseId;
        this.exceptionType = exceptionType;
        this.severity = severity;
        this.sourceAggregateId = sourceAggregateId;
        this.sourceContext = sourceContext;
    }

    @Override
    public String getAggregateType() {
        return "ExceptionCase";
    }
}
