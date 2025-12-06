package com.invoicemanagement.validation.infrastructure.persistence;

import com.invoicemanagement.validation.domain.PayableTransaction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for PayableTransaction aggregate persistence.
 * Uses JSONB columns for complex value objects.
 *
 * Infrastructure Layer
 */
@Entity
@Table(name = "payable_transactions")
@Getter
@Setter
public class PayableTransactionEntity {

    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "invoice_id", nullable = false, unique = true)
    private UUID invoiceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "invoice_reference", nullable = false, columnDefinition = "jsonb")
    private String invoiceReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "po_reference", columnDefinition = "jsonb")
    private String poReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gr_reference", columnDefinition = "jsonb")
    private String grReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matching_result", columnDefinition = "jsonb")
    private String matchingResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compliance_checks", columnDefinition = "jsonb")
    private String complianceChecks;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false)
    private PayableTransaction.ValidationStatus validationStatus;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audit_log", nullable = false, columnDefinition = "jsonb")
    private String auditLog;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

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
