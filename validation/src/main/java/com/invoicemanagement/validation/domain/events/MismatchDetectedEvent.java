package com.invoicemanagement.validation.domain.events;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import com.invoicemanagement.validation.domain.MatchingResult;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Domain event emitted when matching detects variances exceeding tolerance.
 * Triggers ExceptionHandlingContext to create exception case.
 *
 * DDD Pattern: Domain Event
 * Bounded Context: ValidationContext
 * Consumed By: ExceptionHandlingContext
 */
@Getter
@ToString(callSuper = true)
public class MismatchDetectedEvent extends BaseDomainEvent {

    private final UUID transactionId;
    private final UUID invoiceId;
    private final List<MatchingResult.Variance> variances;
    private final BigDecimal matchScore;

    public MismatchDetectedEvent(
        TenantId tenantId,
        UUID transactionId,
        UUID invoiceId,
        List<MatchingResult.Variance> variances,
        BigDecimal matchScore
    ) {
        super(tenantId, transactionId);
        this.transactionId = transactionId;
        this.invoiceId = invoiceId;
        this.variances = variances;
        this.matchScore = matchScore;
    }

    @Override
    public String getAggregateType() {
        return "PayableTransaction";
    }
}
