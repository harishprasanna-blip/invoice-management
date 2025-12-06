package com.invoicemanagement.validation.domain.events;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import com.invoicemanagement.validation.domain.ComplianceCheck;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

/**
 * Domain event emitted when compliance violation is detected.
 * Triggers ExceptionHandlingContext to create exception case.
 *
 * DDD Pattern: Domain Event
 * Bounded Context: ValidationContext
 * Consumed By: ExceptionHandlingContext
 */
@Getter
@ToString(callSuper = true)
public class ComplianceViolationDetectedEvent extends BaseDomainEvent {

    private final UUID transactionId;
    private final UUID invoiceId;
    private final ComplianceCheck.ComplianceCheckType checkType;
    private final String violationDetails;

    public ComplianceViolationDetectedEvent(
        TenantId tenantId,
        UUID transactionId,
        UUID invoiceId,
        ComplianceCheck.ComplianceCheckType checkType,
        String violationDetails
    ) {
        super(tenantId, transactionId);
        this.transactionId = transactionId;
        this.invoiceId = invoiceId;
        this.checkType = checkType;
        this.violationDetails = violationDetails;
    }

    @Override
    public String getAggregateType() {
        return "PayableTransaction";
    }
}
