package com.invoicemanagement.payment.infrastructure.persistence;

import com.invoicemanagement.payment.domain.PaymentRun;
import com.invoicemanagement.payment.domain.PaymentRunRepository;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.tenant.TenantContext;
import com.invoicemanagement.sharedkernel.events.EventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA adapter implementing PaymentRunRepository.
 * 
 * DDD Pattern: Repository Implementation (Adapter in Hexagonal Architecture)
 */
@Component
public class PaymentRunRepositoryAdapter implements PaymentRunRepository {

    private final JpaPaymentRunRepository jpaRepository;
    private final EventPublisher eventPublisher;

    public PaymentRunRepositoryAdapter(
        JpaPaymentRunRepository jpaRepository,
        EventPublisher eventPublisher
    ) {
        this.jpaRepository = jpaRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public PaymentRun save(PaymentRun paymentRun) {
        validateTenantSecurity(paymentRun);

        PaymentRunEntity entity = PaymentRunMapper.toEntity(paymentRun);
        PaymentRunEntity savedEntity = jpaRepository.save(entity);

        // Publish domain events (Transactional Outbox Pattern)
        paymentRun.getDomainEvents().forEach(event ->
            eventPublisher.persist(event)
        );
        paymentRun.clearDomainEvents();

        return PaymentRunMapper.toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentRun> findById(UUID paymentRunId, TenantId tenantId) {
        return jpaRepository.findByIdAndTenant(paymentRunId, tenantId.getId().toString())
            .map(PaymentRunMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentRun> findByInvoiceId(UUID invoiceId, TenantId tenantId) {
        return jpaRepository.findByInvoiceIdAndTenant(invoiceId, tenantId.getId().toString())
            .map(PaymentRunMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentRun> findScheduledPayments(LocalDate scheduledDate, TenantId tenantId) {
        return jpaRepository.findScheduledPayments(scheduledDate, tenantId.getId().toString())
            .stream()
            .map(PaymentRunMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentRun> findPaymentsRequiringApproval(TenantId tenantId) {
        return jpaRepository.findPaymentsRequiringApproval(tenantId.getId().toString())
            .stream()
            .map(PaymentRunMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentRun> findFailedPayments(TenantId tenantId) {
        return jpaRepository.findFailedPayments(tenantId.getId().toString())
            .stream()
            .map(PaymentRunMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentRun> findOverduePayments(TenantId tenantId) {
        return jpaRepository.findOverduePayments(tenantId.getId().toString())
            .stream()
            .map(PaymentRunMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentRun> findByStatus(PaymentRun.PaymentStatus status, TenantId tenantId) {
        return jpaRepository.findByStatusAndTenant(status.name(), tenantId.getId().toString())
            .stream()
            .map(PaymentRunMapper::toDomain)
            .collect(Collectors.toList());
    }

    private void validateTenantSecurity(PaymentRun paymentRun) {
        TenantId currentTenant = TenantContext.getCurrentTenant();
        if (!paymentRun.getTenantId().equals(currentTenant)) {
            throw new SecurityException("Tenant ID mismatch - possible cross-tenant access attempt");
        }
    }
}
