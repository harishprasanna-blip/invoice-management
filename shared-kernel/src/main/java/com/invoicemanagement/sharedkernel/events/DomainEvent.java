package com.invoicemanagement.sharedkernel.events;

import com.invoicemanagement.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Base interface for all domain events.
 * Domain events represent something that happened in the domain (past tense).
 *
 * DDD Pattern: Domain Event
 * Architecture: Event-driven choreography between bounded contexts
 */
public interface DomainEvent {

    /**
     * Unique identifier for this event instance.
     */
    UUID getEventId();

    /**
     * Type of the event (e.g., "InvoiceExtracted", "ValidationPassed").
     */
    String getEventType();

    /**
     * Version of the event schema (for evolution).
     */
    String getEventVersion();

    /**
     * Tenant this event belongs to (multi-tenancy).
     */
    TenantId getTenantId();

    /**
     * ID of the aggregate that emitted this event.
     */
    UUID getAggregateId();

    /**
     * Type of the aggregate (e.g., "Invoice", "PayableTransaction").
     */
    String getAggregateType();

    /**
     * When this event occurred.
     */
    Instant getOccurredAt();

    /**
     * Correlation ID for tracing related events across contexts.
     */
    UUID getCorrelationId();

    /**
     * Causation ID - which event caused this event (for event chains).
     */
    UUID getCausationId();
}
