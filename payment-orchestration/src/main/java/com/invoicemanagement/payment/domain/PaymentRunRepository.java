package com.invoicemanagement.payment.domain;

import com.invoicemanagement.sharedkernel.domain.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for PaymentRun aggregate.
 * 
 * DDD Pattern: Repository (Port in Hexagonal Architecture)
 */
public interface PaymentRunRepository {

    /**
     * Save a new or updated payment run.
     */
    PaymentRun save(PaymentRun paymentRun);

    /**
     * Find payment run by ID within tenant.
     */
    Optional<PaymentRun> findById(UUID paymentRunId, TenantId tenantId);

    /**
     * Find payment run by invoice ID.
     */
    Optional<PaymentRun> findByInvoiceId(UUID invoiceId, TenantId tenantId);

    /**
     * Find all scheduled payments for a given date.
     */
    List<PaymentRun> findScheduledPayments(LocalDate scheduledDate, TenantId tenantId);

    /**
     * Find all payments requiring approval (over threshold).
     */
    List<PaymentRun> findPaymentsRequiringApproval(TenantId tenantId);

    /**
     * Find all failed payments eligible for retry.
     */
    List<PaymentRun> findFailedPayments(TenantId tenantId);

    /**
     * Find overdue payments.
     */
    List<PaymentRun> findOverduePayments(TenantId tenantId);

    /**
     * Find all payments by status.
     */
    List<PaymentRun> findByStatus(PaymentRun.PaymentStatus status, TenantId tenantId);
}
