package com.invoicemanagement.sharedkernel.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event Store entity for persisting domain events.
 * Implements Transactional Outbox Pattern for reliable event publishing.
 *
 * DDD Pattern: Event Sourcing (simplified), Transactional Outbox
 * Architecture: Event-driven, PostgreSQL-based event queue
 *
 * Table: event_store (in common schema, shared across all tenants)
 */
@Entity
@Table(
    name = "event_store",
    schema = "common",
    indexes = {
        @Index(name = "idx_event_tenant_aggregate", columnList = "tenant_id, aggregate_id"),
        @Index(name = "idx_event_type", columnList = "event_type"),
        @Index(name = "idx_event_created", columnList = "created_at"),
        @Index(name = "idx_event_unpublished", columnList = "published_at")
    }
)
@Getter
@NoArgsConstructor
@Slf4j
public class EventStore {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "event_type", nullable = false, length = 200)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private String eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload", nullable = false, columnDefinition = "jsonb")
    private String eventPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by", length = 100)
    private String publishedBy;

    /**
     * Create EventStore entry from DomainEvent.
     */
    public static EventStore fromDomainEvent(DomainEvent event, ObjectMapper objectMapper) {
        EventStore eventStore = new EventStore();
        eventStore.eventId = event.getEventId();
        eventStore.tenantId = event.getTenantId().getId();
        eventStore.aggregateId = event.getAggregateId();
        eventStore.aggregateType = event.getAggregateType();
        eventStore.eventType = event.getEventType();
        eventStore.eventVersion = event.getEventVersion();
        eventStore.createdAt = event.getOccurredAt();

        // Serialize event to JSON
        try {
            eventStore.eventPayload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", event, e);
            throw new RuntimeException("Event serialization failed", e);
        }

        // Create metadata
        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("correlationId", event.getCorrelationId().toString());
        if (event.getCausationId() != null) {
            metadataMap.put("causationId", event.getCausationId().toString());
        }
        metadataMap.put("occurredAt", event.getOccurredAt().toString());

        try {
            eventStore.metadata = objectMapper.writeValueAsString(metadataMap);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize metadata: {}", metadataMap, e);
            throw new RuntimeException("Metadata serialization failed", e);
        }

        return eventStore;
    }

    /**
     * Mark event as published.
     */
    public void markAsPublished(String publisherName) {
        this.publishedAt = Instant.now();
        this.publishedBy = publisherName;
    }

    /**
     * Check if event has been published.
     */
    public boolean isPublished() {
        return publishedAt != null;
    }
}
