package com.invoicemanagement.ingestion.infrastructure.persistence;

import com.invoicemanagement.ingestion.domain.Invoice;
import com.invoicemanagement.ingestion.domain.InvoiceRepository;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.DomainEvent;
import com.invoicemanagement.sharedkernel.events.EventPublisher;
import com.invoicemanagement.sharedkernel.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of InvoiceRepository using Spring Data JPA.
 * Handles aggregate persistence, event publishing, and tenant context.
 *
 * Pattern: Repository Implementation
 * Responsibilities:
 * - Map between domain aggregate and JPA entity
 * - Publish domain events after persistence
 * - Enforce tenant isolation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceRepositoryImpl implements InvoiceRepository {

    private final JpaInvoiceRepository jpaRepository;
    private final InvoiceMapper mapper;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public Invoice save(Invoice invoice) {
        // Ensure tenant context matches aggregate tenant
        TenantId currentTenant = TenantContext.getCurrentTenant();
        if (!invoice.getTenantId().equals(currentTenant)) {
            throw new SecurityException(
                String.format("Tenant mismatch: context=%s, invoice=%s",
                    currentTenant, invoice.getTenantId())
            );
        }

        // Map domain to entity
        InvoiceEntity entity = mapper.toEntity(invoice);

        // Persist entity
        InvoiceEntity savedEntity = jpaRepository.save(entity);

        log.info("Invoice persisted: invoiceId={}, tenantId={}, status={}",
            savedEntity.getInvoiceId(), savedEntity.getTenantId(), savedEntity.getIngestionStatus());

        // Publish domain events (transactional outbox)
        List<DomainEvent> events = invoice.getDomainEventsAndClear();
        events.forEach(eventPublisher::persist);

        log.debug("Published {} domain events for invoice: {}", events.size(), invoice.getInvoiceId());

        // Map back to domain (with updated version from optimistic locking)
        return mapper.toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findById(UUID invoiceId, TenantId tenantId) {
        log.debug("Finding invoice: invoiceId={}, tenantId={}", invoiceId, tenantId);

        return jpaRepository.findByIdAndTenantId(invoiceId, tenantId.getId())
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findById(UUID invoiceId) {
        // Use current tenant context
        TenantId tenantId = TenantContext.getCurrentTenant();
        return findById(invoiceId, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID invoiceId, TenantId tenantId) {
        return jpaRepository.existsByIdAndTenantId(invoiceId, tenantId.getId());
    }

    @Override
    @Transactional
    public void delete(Invoice invoice) {
        // Ensure tenant context matches
        TenantId currentTenant = TenantContext.getCurrentTenant();
        if (!invoice.getTenantId().equals(currentTenant)) {
            throw new SecurityException("Tenant mismatch on delete");
        }

        jpaRepository.deleteById(invoice.getInvoiceId());

        log.info("Invoice deleted: invoiceId={}, tenantId={}",
            invoice.getInvoiceId(), invoice.getTenantId());
    }
}
