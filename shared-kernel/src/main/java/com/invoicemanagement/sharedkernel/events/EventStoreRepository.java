package com.invoicemanagement.sharedkernel.events;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for EventStore persistence.
 * Provides queries for event publishing and replay.
 */
@Repository
public interface EventStoreRepository extends JpaRepository<EventStore, UUID> {

    /**
     * Find all unpublished events for processing.
     * Used by background worker to poll and publish events.
     */
    @Query("SELECT e FROM EventStore e WHERE e.publishedAt IS NULL ORDER BY e.createdAt ASC")
    List<EventStore> findUnpublishedEvents();

    /**
     * Find unpublished events with limit (for batching).
     */
    @Query(value = "SELECT * FROM common.event_store WHERE published_at IS NULL ORDER BY created_at ASC LIMIT :limit", nativeQuery = true)
    List<EventStore> findUnpublishedEventsWithLimit(@Param("limit") int limit);

    /**
     * Find all events for a specific aggregate (for event replay).
     */
    @Query("SELECT e FROM EventStore e WHERE e.aggregateId = :aggregateId ORDER BY e.createdAt ASC")
    List<EventStore> findByAggregateId(@Param("aggregateId") UUID aggregateId);

    /**
     * Find events by tenant.
     */
    @Query("SELECT e FROM EventStore e WHERE e.tenantId = :tenantId ORDER BY e.createdAt DESC")
    List<EventStore> findByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Find events by type (for subscribers).
     */
    @Query("SELECT e FROM EventStore e WHERE e.eventType = :eventType AND e.publishedAt IS NOT NULL ORDER BY e.createdAt ASC")
    List<EventStore> findPublishedEventsByType(@Param("eventType") String eventType);
}
