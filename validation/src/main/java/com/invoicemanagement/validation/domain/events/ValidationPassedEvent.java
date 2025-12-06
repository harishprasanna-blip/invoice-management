package com.invoicemanagement.validation.domain.events;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import com.invoicemanagement.validation.domain.MatchingResult;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain event emitted when validation passes successfully.
 * Triggers PaymentOrchestrationContext to schedule payment.
 *
 * DDD Pattern: Domain Event
 * Bounded Context: ValidationContext
 * Consumed By: PaymentOrchestrationContext
 */
@Getter
@ToString(callSuper = true)
public class ValidationPassedEvent extends BaseDomainEvent {

    private final UUID transactionId;
    private final UUID invoiceId;
    private final MatchingResult.MatchType matchType;
    private final BigDecimal matchScore;

    public ValidationPassedEvent(
        TenantId tenantId,
        UUID transactionId,
        UUID invoiceId,
        MatchingResult.MatchType matchType,
        BigDecimal matchScore
    ) {
        super(tenantId, transactionId);
        this.transactionId = transactionId;
        this.invoiceId = invoiceId;
        this.matchType = matchType;
        this.matchScore = matchScore;
    }

    @Override
    public String getAggregateType() {
        return "PayableTransaction";
    }
}
