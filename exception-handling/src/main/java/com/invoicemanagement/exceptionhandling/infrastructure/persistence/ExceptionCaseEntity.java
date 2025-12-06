package com.invoicemanagement.exceptionhandling.infrastructure.persistence;

import com.invoicemanagement.exceptionhandling.domain.ExceptionCase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for ExceptionCase aggregate persistence.
 * Uses JSONB columns for complex value objects.
 *
 * Infrastructure Layer
 */
@Entity
@Table(name = "exception_cases")
@Getter
@Setter
public class ExceptionCaseEntity {

    @Id
    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false)
    private ExceptionCase.ExceptionType exceptionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private ExceptionCase.ExceptionSeverity severity;

    @Column(name = "source_aggregate_id", nullable = false)
    private UUID sourceAggregateId;

    @Column(name = "source_context", nullable = false)
    private String sourceContext;

    @Column(name = "exception_details", nullable = false, columnDefinition = "TEXT")
    private String exceptionDetails;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExceptionCase.ExceptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_strategy")
    private com.invoicemanagement.exceptionhandling.domain.ResolutionStrategy resolutionStrategy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ml_recommendations", columnDefinition = "jsonb")
    private String mlRecommendations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolution_history", columnDefinition = "jsonb")
    private String resolutionHistory;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_level", nullable = false)
    private ExceptionCase.EscalationLevel escalationLevel;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sla_deadline", nullable = false)
    private Instant slaDeadline;

    @Column(name = "sla_breached", nullable = false)
    private boolean slaBreached;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audit_log", nullable = false, columnDefinition = "jsonb")
    private String auditLog;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
