package com.invoicemanagement.payment.domain.events;

import com.invoicemanagement.sharedkernel.domain.Money;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Event emitted when a payment is scheduled.
 * Consumed by: Reporting/Analytics services
 */
@Getter
public class PaymentScheduledEvent extends BaseDomainEvent {

    private final UUID paymentRunId;
    private final UUID invoiceId;
    private final Money amount;
    private final LocalDate scheduledDate;

    public PaymentScheduledEvent(
        TenantId tenantId,
        UUID paymentRunId,
        UUID invoiceId,
        Money amount,
        LocalDate scheduledDate
    ) {
        super(tenantId, paymentRunId);
        this.paymentRunId = paymentRunId;
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.scheduledDate = scheduledDate;
    }

    @Override
    public String getEventType() {
        return "PaymentScheduled";
    }

    @Override
    public String getAggregateType() {
        return "PaymentRun";
    }
}
