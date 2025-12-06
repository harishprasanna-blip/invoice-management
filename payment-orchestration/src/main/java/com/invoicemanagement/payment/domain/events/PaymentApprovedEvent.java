package com.invoicemanagement.payment.domain.events;

import com.invoicemanagement.sharedkernel.domain.Money;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.BaseDomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Event emitted when a payment is approved.
 * Triggers SAP posting workflow.
 */
@Getter
public class PaymentApprovedEvent extends BaseDomainEvent {

    private final UUID paymentRunId;
    private final UUID invoiceId;
    private final String approvedBy;
    private final Money amount;

    public PaymentApprovedEvent(
        TenantId tenantId,
        UUID paymentRunId,
        UUID invoiceId,
        String approvedBy,
        Money amount
    ) {
        super(tenantId, paymentRunId);
        this.paymentRunId = paymentRunId;
        this.invoiceId = invoiceId;
        this.approvedBy = approvedBy;
        this.amount = amount;
    }

    @Override
    public String getEventType() {
        return "PaymentApproved";
    }

    @Override
    public String getAggregateType() {
        return "PaymentRun";
    }
}
