package com.invoicemanagement.sharedkernel.events;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Abstract base class for domain events with common metadata.
 * Provides default implementation of DomainEvent interface.
 *
 * DDD Pattern: Domain Event
 */
@Getter
@EqualsAndHashCode
@ToString
public abstract class BaseDomainEvent implements DomainEvent {

    private final UUID eventId;
    private final String eventVersion;
    private final TenantId tenantId;
    private final UUID aggregateId;
    private final Instant occurredAt;
    private final UUID correlationId;
    private final UUID causationId;

    protected BaseDomainEvent(
        TenantId tenantId,
        UUID aggregateId,
        UUID correlationId,
        UUID causationId
    ) {
        this.eventId = UUID.randomUUID();
        this.eventVersion = "1.0";
        this.tenantId = Objects.requireNonNull(tenantId, "TenantId cannot be null");
        this.aggregateId = Objects.requireNonNull(aggregateId, "AggregateId cannot be null");
        this.occurredAt = Instant.now();
        this.correlationId = correlationId != null ? correlationId : UUID.randomUUID();
        this.causationId = causationId;
    }

    /**
     * Convenience constructor for new event chains.
     */
    protected BaseDomainEvent(TenantId tenantId, UUID aggregateId) {
        this(tenantId, aggregateId, UUID.randomUUID(), null);
    }

    @Override
    public String getEventType() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String getAggregateType() {
        // Override in subclasses to specify aggregate type
        return "Unknown";
    }
}
