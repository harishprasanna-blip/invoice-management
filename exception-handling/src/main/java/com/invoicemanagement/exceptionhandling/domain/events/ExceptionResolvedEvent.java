package com.invoicemanagement.exceptionhandling.domain.events;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import com.invoicemanagement.exceptionhandling.domain.ExceptionCase;
import com.invoicemanagement.exceptionhandling.domain.ResolutionStrategy;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * Domain event emitted when exception case is resolved.
 * Triggers original context to retry/continue workflow.
 *
 * DDD Pattern: Domain Event
 * Bounded Context: ExceptionHandlingContext
 * Consumed By: ValidationContext, InvoiceIngestionContext, PaymentOrchestrationContext
 */
@Getter
@ToString(callSuper = true)
public class ExceptionResolvedEvent extends BaseDomainEvent {

    private final UUID caseId;
    private final UUID sourceAggregateId;
    private final ExceptionCase.ExceptionType exceptionType;
    private final ResolutionStrategy resolutionStrategy;
    private final UUID resolvedBy;

    public ExceptionResolvedEvent(
        TenantId tenantId,
        UUID caseId,
        UUID sourceAggregateId,
        ExceptionCase.ExceptionType exceptionType,
        ResolutionStrategy resolutionStrategy,
        UUID resolvedBy
    ) {
        super(tenantId, caseId);
        this.caseId = caseId;
        this.sourceAggregateId = sourceAggregateId;
        this.exceptionType = exceptionType;
        this.resolutionStrategy = resolutionStrategy;
        this.resolvedBy = resolvedBy;
    }

    @Override
    public String getAggregateType() {
        return "ExceptionCase";
    }
}
