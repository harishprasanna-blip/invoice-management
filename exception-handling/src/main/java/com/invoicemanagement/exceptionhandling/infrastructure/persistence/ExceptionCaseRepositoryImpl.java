package com.invoicemanagement.exceptionhandling.infrastructure.persistence;

import com.invoicemanagement.sharedkernel.events.DomainEvent;
import com.invoicemanagement.sharedkernel.events.EventPublisher;
import com.invoicemanagement.sharedkernel.tenant.TenantContext;
import com.invoicemanagement.exceptionhandling.domain.ExceptionCase;
import com.invoicemanagement.exceptionhandling.domain.ExceptionCaseRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Repository implementation (Adapter) for ExceptionCase aggregate.
 * Enforces tenant security and publishes domain events.
 *
 * DDD Pattern: Repository (Adapter)
 * Bounded Context: ExceptionHandlingContext
 */
@Repository
public class ExceptionCaseRepositoryImpl implements ExceptionCaseRepository {

    private final JpaExceptionCaseRepository jpaRepository;
    private final ExceptionCaseMapper mapper;
    private final EventPublisher eventPublisher;

    public ExceptionCaseRepositoryImpl(
        JpaExceptionCaseRepository jpaRepository,
        ExceptionCaseMapper mapper,
        EventPublisher eventPublisher
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ExceptionCase save(ExceptionCase exceptionCase) {
        validateTenantSecurity(exceptionCase);

        ExceptionCaseEntity entity = mapper.toEntity(exceptionCase);
        ExceptionCaseEntity savedEntity = jpaRepository.save(entity);

        // Publish domain events
        List<DomainEvent> events = exceptionCase.getDomainEventsAndClear();
        events.forEach(eventPublisher::persist);

        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<ExceptionCase> findById(UUID caseId) {
        return jpaRepository.findById(caseId)
            .map(entity -> {
                ExceptionCase exceptionCase = mapper.toDomain(entity);
                validateTenantSecurity(exceptionCase);
                return exceptionCase;
            });
    }

    @Override
    public Optional<ExceptionCase> findBySourceAggregateId(UUID sourceAggregateId) {
        return jpaRepository.findBySourceAggregateId(sourceAggregateId)
            .map(entity -> {
                ExceptionCase exceptionCase = mapper.toDomain(entity);
                validateTenantSecurity(exceptionCase);
                return exceptionCase;
            });
    }

    @Override
    public List<ExceptionCase> findOpenCases() {
        return jpaRepository.findOpenCases().stream()
            .map(mapper::toDomain)
            .filter(this::belongsToCurrentTenant)
            .collect(Collectors.toList());
    }

    @Override
    public List<ExceptionCase> findByStatus(ExceptionCase.ExceptionStatus status) {
        return jpaRepository.findByStatus(status).stream()
            .map(mapper::toDomain)
            .filter(this::belongsToCurrentTenant)
            .collect(Collectors.toList());
    }

    @Override
    public List<ExceptionCase> findByAssignee(String assignee) {
        return jpaRepository.findByAssignedTo(assignee).stream()
            .map(mapper::toDomain)
            .filter(this::belongsToCurrentTenant)
            .collect(Collectors.toList());
    }

    @Override
    public List<ExceptionCase> findSLABreachedCases() {
        return jpaRepository.findSLABreachedCases().stream()
            .map(mapper::toDomain)
            .filter(this::belongsToCurrentTenant)
            .collect(Collectors.toList());
    }

    @Override
    public List<ExceptionCase> findCasesRequiringEscalation(Instant threshold) {
        return jpaRepository.findCasesRequiringEscalation(threshold).stream()
            .map(mapper::toDomain)
            .filter(this::belongsToCurrentTenant)
            .collect(Collectors.toList());
    }

    private void validateTenantSecurity(ExceptionCase exceptionCase) {
        if (!exceptionCase.getTenantId().equals(TenantContext.getCurrentTenant())) {
            throw new SecurityException(
                "Tenant mismatch: Cannot access exception case from different tenant"
            );
        }
    }

    private boolean belongsToCurrentTenant(ExceptionCase exceptionCase) {
        return exceptionCase.getTenantId().equals(TenantContext.getCurrentTenant());
    }
}
