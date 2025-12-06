package com.invoicemanagement.payment.domain.events;

import com.invoicemanagement.payment.domain.PaymentRun;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * Event emitted when payment fails.
 * Triggers compensating actions in Saga pattern.
 */
@Getter
public class PaymentFailedEvent extends BaseDomainEvent {

    private final UUID paymentRunId;
    private final UUID invoiceId;
    private final String failureReason;
    private final List<PaymentRun.CompensationAction> compensationActions;

    public PaymentFailedEvent(
        TenantId tenantId,
        UUID paymentRunId,
        UUID invoiceId,
        String failureReason,
        List<PaymentRun.CompensationAction> compensationActions
    ) {
        super(tenantId, paymentRunId);
        this.paymentRunId = paymentRunId;
        this.invoiceId = invoiceId;
        this.failureReason = failureReason;
        this.compensationActions = compensationActions;
    }

    @Override
    public String getEventType() {
        return "PaymentFailed";
    }

    @Override
    public String getAggregateType() {
        return "PaymentRun";
    }
}
