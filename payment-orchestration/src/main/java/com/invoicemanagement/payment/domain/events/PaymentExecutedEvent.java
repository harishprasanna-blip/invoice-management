package com.invoicemanagement.payment.domain.events;

import com.invoicemanagement.sharedkernel.domain.Money;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when payment is successfully executed.
 * Marks end of payment lifecycle.
 */
@Getter
public class PaymentExecutedEvent extends BaseDomainEvent {

    private final UUID paymentRunId;
    private final UUID invoiceId;
    private final String gatewayTransactionId;
    private final Money amount;
    private final Instant executedAt;

    public PaymentExecutedEvent(
        TenantId tenantId,
        UUID paymentRunId,
        UUID invoiceId,
        String gatewayTransactionId,
        Money amount,
        Instant executedAt
    ) {
        super(tenantId, paymentRunId);
        this.paymentRunId = paymentRunId;
        this.invoiceId = invoiceId;
        this.gatewayTransactionId = gatewayTransactionId;
        this.amount = amount;
        this.executedAt = executedAt;
    }

    @Override
    public String getEventType() {
        return "PaymentExecuted";
    }

    @Override
    public String getAggregateType() {
        return "PaymentRun";
    }
}
