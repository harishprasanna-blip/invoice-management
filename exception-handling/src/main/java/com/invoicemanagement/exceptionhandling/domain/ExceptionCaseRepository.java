package com.invoicemanagement.exceptionhandling.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ExceptionCase aggregate.
 * Port (interface) for hexagonal architecture.
 *
 * DDD Pattern: Repository (Port)
 * Bounded Context: ExceptionHandlingContext
 */
public interface ExceptionCaseRepository {

    /**
     * Save exception case.
     * Publishes domain events.
     */
    ExceptionCase save(ExceptionCase exceptionCase);

    /**
     * Find case by ID.
     */
    Optional<ExceptionCase> findById(UUID caseId);

    /**
     * Find case by source aggregate ID.
     */
    Optional<ExceptionCase> findBySourceAggregateId(UUID sourceAggregateId);

    /**
     * Find all open cases for tenant.
     */
    List<ExceptionCase> findOpenCases();

    /**
     * Find cases by status.
     */
    List<ExceptionCase> findByStatus(ExceptionCase.ExceptionStatus status);

    /**
     * Find cases assigned to specific resolver.
     */
    List<ExceptionCase> findByAssignee(String assignee);

    /**
     * Find cases breaching SLA.
     */
    List<ExceptionCase> findSLABreachedCases();

    /**
     * Find cases requiring escalation.
     */
    List<ExceptionCase> findCasesRequiringEscalation(Instant threshold);
}
