package com.invoicemanagement.sharedkernel.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for publishing domain events.
 * Implements Transactional Outbox Pattern:
 * 1. Event persisted in event_store within same transaction as aggregate
 * 2. Background worker polls unpublished events
 * 3. Worker publishes to internal Spring ApplicationEventPublisher
 * 4. Worker marks event as published
 *
 * DDD Pattern: Domain Event Publishing
 * Reliability: At-least-once delivery guarantee
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final EventStoreRepository eventStoreRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Persist event to event store (within aggregate transaction).
     * Does NOT publish immediately - background worker will publish.
     */
    @Transactional
    public void persist(DomainEvent event) {
        log.debug("Persisting event: {} for aggregate: {}", event.getEventType(), event.getAggregateId());

        EventStore eventStore = EventStore.fromDomainEvent(event, objectMapper);
        eventStoreRepository.save(eventStore);

        log.info("Event persisted: eventId={}, type={}, aggregateId={}",
            eventStore.getEventId(), eventStore.getEventType(), eventStore.getAggregateId());
    }

    /**
     * Publish event to internal Spring event bus (called by background worker).
     * Marks event as published in database.
     */
    @Transactional
    public void publish(EventStore eventStore) {
        if (eventStore.isPublished()) {
            log.warn("Event already published: {}", eventStore.getEventId());
            return;
        }

        try {
            // Publish to Spring ApplicationEventPublisher for internal consumption
            // Subscribers will use @EventListener to consume
            applicationEventPublisher.publishEvent(eventStore);

            // Mark as published
            eventStore.markAsPublished("EventPublisher");
            eventStoreRepository.save(eventStore);

            log.info("Event published: eventId={}, type={}", eventStore.getEventId(), eventStore.getEventType());

        } catch (Exception e) {
            log.error("Failed to publish event: {}", eventStore.getEventId(), e);
            // Event remains unpublished and will be retried by worker
            throw e;
        }
    }

    /**
     * Convenience method to persist and publish immediately (for testing).
     * Production code should use persist() and let background worker publish.
     */
    @Transactional
    public void persistAndPublish(DomainEvent event) {
        EventStore eventStore = EventStore.fromDomainEvent(event, objectMapper);
        eventStoreRepository.save(eventStore);
        publish(eventStore);
    }
}
