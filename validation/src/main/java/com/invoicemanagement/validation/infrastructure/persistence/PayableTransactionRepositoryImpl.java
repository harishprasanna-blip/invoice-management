package com.invoicemanagement.validation.infrastructure.persistence;

import com.invoicemanagement.sharedkernel.events.DomainEvent;
import com.invoicemanagement.sharedkernel.events.EventPublisher;
import com.invoicemanagement.sharedkernel.tenant.TenantContext;
import com.invoicemanagement.validation.domain.PayableTransaction;
import com.invoicemanagement.validation.domain.PayableTransactionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository implementation (Adapter) for PayableTransaction aggregate.
 * Enforces tenant security and publishes domain events.
 *
 * DDD Pattern: Repository (Adapter)
 * Bounded Context: ValidationContext
 */
@Repository
public class PayableTransactionRepositoryImpl implements PayableTransactionRepository {

    private final JpaPayableTransactionRepository jpaRepository;
    private final PayableTransactionMapper mapper;
    private final EventPublisher eventPublisher;

    public PayableTransactionRepositoryImpl(
        JpaPayableTransactionRepository jpaRepository,
        PayableTransactionMapper mapper,
        EventPublisher eventPublisher
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public PayableTransaction save(PayableTransaction transaction) {
        // Validate tenant context matches aggregate tenant
        validateTenantSecurity(transaction);

        // Convert to entity
        PayableTransactionEntity entity = mapper.toEntity(transaction);

        // Save entity
        PayableTransactionEntity savedEntity = jpaRepository.save(entity);

        // Publish domain events (transactional outbox)
        List<DomainEvent> events = transaction.getDomainEventsAndClear();
        for (DomainEvent event : events) {
            eventPublisher.persist(event);
        }

        // Convert back to domain
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<PayableTransaction> findById(UUID transactionId) {
        return jpaRepository.findById(transactionId)
            .map(entity -> {
                PayableTransaction transaction = mapper.toDomain(entity);
                validateTenantSecurity(transaction);
                return transaction;
            });
    }

    @Override
    public Optional<PayableTransaction> findByInvoiceId(UUID invoiceId) {
        return jpaRepository.findByInvoiceId(invoiceId)
            .map(entity -> {
                PayableTransaction transaction = mapper.toDomain(entity);
                validateTenantSecurity(transaction);
                return transaction;
            });
    }

    @Override
    public boolean existsByInvoiceId(UUID invoiceId) {
        return jpaRepository.existsByInvoiceId(invoiceId);
    }

    /**
     * Validate that current tenant context matches aggregate tenant.
     * Prevents cross-tenant data access.
     */
    private void validateTenantSecurity(PayableTransaction transaction) {
        if (!transaction.getTenantId().equals(TenantContext.getCurrentTenant())) {
            throw new SecurityException(
                "Tenant mismatch: Cannot access transaction from different tenant"
            );
        }
    }
}
