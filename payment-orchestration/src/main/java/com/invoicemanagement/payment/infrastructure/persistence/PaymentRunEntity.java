package com.invoicemanagement.payment.infrastructure.persistence;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity for PaymentRun aggregate.
 */
@Entity
@Table(name = "payment_runs", schema = "common")
@Getter
@Setter
public class PaymentRunEntity {

    @Id
    @Column(name = "payment_run_id", nullable = false)
    private UUID paymentRunId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "invoice_number", nullable = false, length = 100)
    private String invoiceNumber;

    @Column(name = "total_amount_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmountValue;

    @Column(name = "total_amount_currency", nullable = false, length = 3)
    private String totalAmountCurrency;

    @Column(name = "vendor_id", nullable = false, length = 100)
    private String vendorId;

    @Column(name = "vendor_bank_account", nullable = false, length = 100)
    private String vendorBankAccount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "scheduled_payment_date", nullable = false)
    private LocalDate scheduledPaymentDate;

    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod;

    @Column(name = "payment_status", nullable = false, length = 50)
    private String paymentStatus;

    @Column(name = "saga_state", nullable = false, length = 50)
    private String sagaState;

    @Type(JsonBinaryType.class)
    @Column(name = "completed_steps", columnDefinition = "jsonb")
    private List<PaymentRunMapper.SagaStepJson> completedSteps;

    @Type(JsonBinaryType.class)
    @Column(name = "compensation_actions", columnDefinition = "jsonb")
    private List<PaymentRunMapper.CompensationJson> compensationActions;

    @Column(name = "sap_document_number", length = 50)
    private String sapDocumentNumber;

    @Column(name = "sap_posting_result", columnDefinition = "TEXT")
    private String sapPostingResult;

    @Column(name = "gateway_transaction_id", length = 100)
    private String gatewayTransactionId;

    @Column(name = "gateway_response", columnDefinition = "TEXT")
    private String gatewayResponse;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approval_notes", columnDefinition = "TEXT")
    private String approvalNotes;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Type(JsonBinaryType.class)
    @Column(name = "audit_log", columnDefinition = "jsonb", nullable = false)
    private PaymentRunMapper.AuditLogJson auditLogJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
