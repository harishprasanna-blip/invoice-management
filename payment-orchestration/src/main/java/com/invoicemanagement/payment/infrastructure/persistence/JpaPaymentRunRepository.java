package com.invoicemanagement.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for PaymentRunEntity.
 */
@Repository
public interface JpaPaymentRunRepository extends JpaRepository<PaymentRunEntity, UUID> {

    @Query("SELECT p FROM PaymentRunEntity p WHERE p.paymentRunId = :id AND p.tenantId = :tenantId")
    Optional<PaymentRunEntity> findByIdAndTenant(
        @Param("id") UUID id,
        @Param("tenantId") String tenantId
    );

    @Query("SELECT p FROM PaymentRunEntity p WHERE p.invoiceId = :invoiceId AND p.tenantId = :tenantId")
    Optional<PaymentRunEntity> findByInvoiceIdAndTenant(
        @Param("invoiceId") UUID invoiceId,
        @Param("tenantId") String tenantId
    );

    @Query("SELECT p FROM PaymentRunEntity p WHERE p.scheduledPaymentDate = :date AND p.tenantId = :tenantId AND p.paymentStatus = 'APPROVED'")
    List<PaymentRunEntity> findScheduledPayments(
        @Param("date") LocalDate date,
        @Param("tenantId") String tenantId
    );

    @Query("SELECT p FROM PaymentRunEntity p WHERE p.paymentStatus = 'SCHEDULED' AND p.tenantId = :tenantId AND p.totalAmountValue > 10000")
    List<PaymentRunEntity> findPaymentsRequiringApproval(@Param("tenantId") String tenantId);

    @Query("SELECT p FROM PaymentRunEntity p WHERE p.paymentStatus = 'FAILED' AND p.tenantId = :tenantId AND p.retryCount < 3")
    List<PaymentRunEntity> findFailedPayments(@Param("tenantId") String tenantId);

    @Query("SELECT p FROM PaymentRunEntity p WHERE p.dueDate < CURRENT_DATE AND p.paymentStatus != 'EXECUTED' AND p.tenantId = :tenantId")
    List<PaymentRunEntity> findOverduePayments(@Param("tenantId") String tenantId);

    @Query("SELECT p FROM PaymentRunEntity p WHERE p.paymentStatus = :status AND p.tenantId = :tenantId")
    List<PaymentRunEntity> findByStatusAndTenant(
        @Param("status") String status,
        @Param("tenantId") String tenantId
    );
}
