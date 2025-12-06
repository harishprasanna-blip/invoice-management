package com.invoicemanagement.exceptionhandling.infrastructure.persistence;

import com.invoicemanagement.exceptionhandling.domain.ExceptionCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository interface.
 */
@Repository
public interface JpaExceptionCaseRepository extends JpaRepository<ExceptionCaseEntity, UUID> {

    Optional<ExceptionCaseEntity> findBySourceAggregateId(UUID sourceAggregateId);

    @Query("SELECT e FROM ExceptionCaseEntity e WHERE e.status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'ESCALATED')")
    List<ExceptionCaseEntity> findOpenCases();

    List<ExceptionCaseEntity> findByStatus(ExceptionCase.ExceptionStatus status);

    List<ExceptionCaseEntity> findByAssignedTo(String assignedTo);

    @Query("SELECT e FROM ExceptionCaseEntity e WHERE e.slaBreached = true AND e.status NOT IN ('RESOLVED', 'CLOSED')")
    List<ExceptionCaseEntity> findSLABreachedCases();

    @Query("SELECT e FROM ExceptionCaseEntity e WHERE e.assignedAt < :threshold AND e.status NOT IN ('RESOLVED', 'CLOSED')")
    List<ExceptionCaseEntity> findCasesRequiringEscalation(@Param("threshold") Instant threshold);
}
