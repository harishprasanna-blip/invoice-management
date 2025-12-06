package com.invoicemanagement.exceptionhandling.domain;

import com.invoicemanagement.sharedkernel.domain.AuditLog;
import com.invoicemanagement.sharedkernel.domain.AuditLogHelper;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.DomainEvent;
import com.invoicemanagement.exceptionhandling.domain.events.ExceptionCreatedEvent;
import com.invoicemanagement.exceptionhandling.domain.events.ExceptionEscalatedEvent;
import com.invoicemanagement.exceptionhandling.domain.events.ExceptionResolvedEvent;
import com.invoicemanagement.exceptionhandling.domain.events.SLABreachedEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root representing an exception case requiring resolution.
 * Contains exception details, ML recommendations, and resolution history.
 *
 * DDD Pattern: Aggregate Root
 * Bounded Context: ExceptionHandlingContext
 * Consistency Boundary: All exception resolution actions
 */
@Getter
@EqualsAndHashCode(of = "caseId")
@ToString
public class ExceptionCase implements Serializable {

    // Identity
    private final UUID caseId;
    private final TenantId tenantId;

    // Exception details
    private final ExceptionType exceptionType;
    private final ExceptionSeverity severity;
    private final UUID sourceAggregateId; // Invoice, Transaction, etc.
    private final String sourceContext; // Which bounded context created this
    private final String exceptionDetails;

    // Resolution
    private ExceptionStatus status;
    private ResolutionStrategy resolutionStrategy;
    private final List<MLRecommendation> mlRecommendations;
    private final List<ResolutionAction> resolutionHistory;
    private String resolutionNotes;
    private UUID resolvedBy;
    private Instant resolvedAt;

    // Escalation
    private EscalationLevel escalationLevel;
    private String assignedTo;
    private Instant assignedAt;

    // SLA tracking
    private final Instant createdAt;
    private final Instant slaDeadline;
    private boolean slaBreached;

    // Audit trail (immutable value object - reassign on update)
    private AuditLog auditLog;

    // Domain events (not persisted)
    private transient List<DomainEvent> domainEvents = new ArrayList<>();

    private ExceptionCase(
        UUID caseId,
        TenantId tenantId,
        ExceptionType exceptionType,
        ExceptionSeverity severity,
        UUID sourceAggregateId,
        String sourceContext,
        String exceptionDetails,
        ExceptionStatus status,
        ResolutionStrategy resolutionStrategy,
        List<MLRecommendation> mlRecommendations,
        List<ResolutionAction> resolutionHistory,
        String resolutionNotes,
        UUID resolvedBy,
        Instant resolvedAt,
        EscalationLevel escalationLevel,
        String assignedTo,
        Instant assignedAt,
        Instant createdAt,
        Instant slaDeadline,
        boolean slaBreached,
        AuditLog auditLog
    ) {
        this.caseId = Objects.requireNonNull(caseId, "Case ID cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID cannot be null");
        this.exceptionType = Objects.requireNonNull(exceptionType, "Exception type cannot be null");
        this.severity = Objects.requireNonNull(severity, "Severity cannot be null");
        this.sourceAggregateId = Objects.requireNonNull(sourceAggregateId, "Source aggregate ID cannot be null");
        this.sourceContext = Objects.requireNonNull(sourceContext, "Source context cannot be null");
        this.exceptionDetails = Objects.requireNonNull(exceptionDetails, "Exception details cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.resolutionStrategy = resolutionStrategy;
        this.mlRecommendations = new ArrayList<>(mlRecommendations);
        this.resolutionHistory = new ArrayList<>(resolutionHistory);
        this.resolutionNotes = resolutionNotes;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
        this.escalationLevel = Objects.requireNonNull(escalationLevel, "Escalation level cannot be null");
        this.assignedTo = assignedTo;
        this.assignedAt = assignedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
        this.slaDeadline = Objects.requireNonNull(slaDeadline, "SLA deadline cannot be null");
        this.slaBreached = slaBreached;
        this.auditLog = Objects.requireNonNull(auditLog, "Audit log cannot be null");
    }

    /**
     * Factory method for creating new exception case.
     * Emits ExceptionCreatedEvent.
     */
    public static ExceptionCase create(
        TenantId tenantId,
        ExceptionType exceptionType,
        ExceptionSeverity severity,
        UUID sourceAggregateId,
        String sourceContext,
        String exceptionDetails
    ) {
        UUID caseId = UUID.randomUUID();
        Instant now = Instant.now();

        // Calculate SLA deadline based on severity
        Instant slaDeadline = calculateSLADeadline(now, severity);

        // Determine initial escalation level based on severity
        EscalationLevel escalationLevel = determineInitialEscalationLevel(severity);

        AuditLog auditLog = AuditLogHelper.addEntry(
            AuditLog.empty(),
            "EXCEPTION_CREATED",
            null,
            "exceptionType",
            exceptionType.name(),
            tenantId
        );

        ExceptionCase exceptionCase = new ExceptionCase(
            caseId,
            tenantId,
            exceptionType,
            severity,
            sourceAggregateId,
            sourceContext,
            exceptionDetails,
            ExceptionStatus.OPEN,
            null, // No resolution strategy yet
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            null,
            null,
            escalationLevel,
            null, // Not yet assigned
            null,
            now,
            slaDeadline,
            false,
            auditLog
        );

        exceptionCase.addDomainEvent(new ExceptionCreatedEvent(
            tenantId,
            caseId,
            exceptionType,
            severity,
            sourceAggregateId,
            sourceContext
        ));

        return exceptionCase;
    }

    /**
     * Factory method for reconstituting from persistence.
     */
    public static ExceptionCase reconstitute(
        UUID caseId,
        TenantId tenantId,
        ExceptionType exceptionType,
        ExceptionSeverity severity,
        UUID sourceAggregateId,
        String sourceContext,
        String exceptionDetails,
        ExceptionStatus status,
        ResolutionStrategy resolutionStrategy,
        List<MLRecommendation> mlRecommendations,
        List<ResolutionAction> resolutionHistory,
        String resolutionNotes,
        UUID resolvedBy,
        Instant resolvedAt,
        EscalationLevel escalationLevel,
        String assignedTo,
        Instant assignedAt,
        Instant createdAt,
        Instant slaDeadline,
        boolean slaBreached,
        AuditLog auditLog
    ) {
        return new ExceptionCase(
            caseId, tenantId, exceptionType, severity, sourceAggregateId, sourceContext,
            exceptionDetails, status, resolutionStrategy, mlRecommendations, resolutionHistory,
            resolutionNotes, resolvedBy, resolvedAt, escalationLevel, assignedTo, assignedAt,
            createdAt, slaDeadline, slaBreached, auditLog
        );
    }

    /**
     * Add ML recommendation from AI service.
     */
    public void addMLRecommendation(MLRecommendation recommendation) {
        Objects.requireNonNull(recommendation, "ML recommendation cannot be null");

        this.mlRecommendations.add(recommendation);

        this.auditLog = AuditLogHelper.addEntry(
            this.auditLog,
            "ML_RECOMMENDATION_ADDED",
            null,
            "confidence",
            String.valueOf(recommendation.getConfidence()),
            this.tenantId
        );

        // Auto-apply recommendation if confidence is high enough
        if (recommendation.getConfidence().compareTo(java.math.BigDecimal.valueOf(0.85)) >= 0 && recommendation.isAutomatable()) {
            this.resolutionStrategy = recommendation.getRecommendedStrategy();
            this.auditLog = AuditLogHelper.addEntry(
                this.auditLog,
                "RESOLUTION_STRATEGY_SET",
                null,
                "strategy",
                resolutionStrategy.name(),
                this.tenantId
            );
        }
    }

    /**
     * Assign case to resolver (human or automated system).
     */
    public void assign(String assignee, EscalationLevel newEscalationLevel) {
        Objects.requireNonNull(assignee, "Assignee cannot be null");

        this.assignedTo = assignee;
        this.assignedAt = Instant.now();
        this.escalationLevel = newEscalationLevel;
        this.status = ExceptionStatus.ASSIGNED;

        this.auditLog = AuditLogHelper.addEntry(
            this.auditLog,
            "CASE_ASSIGNED",
            null,
            "assignedTo",
            assignee,
            this.tenantId
        );
    }

    /**
     * Escalate case to higher level.
     */
    public void escalate(EscalationLevel newLevel, String reason) {
        if (newLevel.ordinal() <= this.escalationLevel.ordinal()) {
            throw new IllegalArgumentException("New escalation level must be higher than current level");
        }

        this.escalationLevel = newLevel;
        this.status = ExceptionStatus.ESCALATED;

        this.auditLog = AuditLogHelper.addEntry(
            this.auditLog,
            "CASE_ESCALATED",
            null,
            "newLevel",
            newLevel.name(),
            this.tenantId
        );

        addDomainEvent(new ExceptionEscalatedEvent(
            this.tenantId,
            this.caseId,
            this.sourceAggregateId,
            newLevel,
            reason
        ));
    }

    /**
     * Add resolution action to history.
     */
    public void addResolutionAction(ResolutionAction action) {
        Objects.requireNonNull(action, "Resolution action cannot be null");

        this.resolutionHistory.add(action);
        this.status = ExceptionStatus.IN_PROGRESS;

        this.auditLog = AuditLogHelper.addEntry(
            this.auditLog,
            "RESOLUTION_ACTION_TAKEN",
            null,
            "actionType",
            action.getActionType().name(),
            this.tenantId
        );
    }

    /**
     * Resolve the exception case.
     */
    public void resolve(UUID resolverUserId, String notes) {
        if (this.status == ExceptionStatus.RESOLVED) {
            throw new IllegalStateException("Case already resolved");
        }

        this.status = ExceptionStatus.RESOLVED;
        this.resolvedBy = Objects.requireNonNull(resolverUserId, "Resolver user ID cannot be null");
        this.resolvedAt = Instant.now();
        this.resolutionNotes = notes;

        this.auditLog = AuditLogHelper.addEntry(
            this.auditLog,
            "CASE_RESOLVED",
            null,
            "resolvedBy",
            resolverUserId.toString(),
            this.tenantId
        );

        addDomainEvent(new ExceptionResolvedEvent(
            this.tenantId,
            this.caseId,
            this.sourceAggregateId,
            this.exceptionType,
            this.resolutionStrategy,
            resolverUserId
        ));
    }

    /**
     * Check if SLA has been breached.
     */
    public void checkSLA() {
        if (!slaBreached && Instant.now().isAfter(slaDeadline)) {
            this.slaBreached = true;

            this.auditLog = AuditLogHelper.addEntry(
                this.auditLog,
                "SLA_BREACHED",
                null,
                "deadline",
                slaDeadline.toString(),
                this.tenantId
            );

            addDomainEvent(new SLABreachedEvent(
                this.tenantId,
                this.caseId,
                this.sourceAggregateId,
                Duration.between(slaDeadline, Instant.now())
            ));
        }
    }

    /**
     * Get time until SLA deadline.
     */
    public Duration getTimeUntilSLA() {
        return Duration.between(Instant.now(), slaDeadline);
    }

    /**
     * Check if case is overdue.
     */
    public boolean isOverdue() {
        return Instant.now().isAfter(slaDeadline);
    }

    /**
     * Get immutable copies of collections.
     */
    public List<MLRecommendation> getMlRecommendations() {
        return Collections.unmodifiableList(mlRecommendations);
    }

    public List<ResolutionAction> getResolutionHistory() {
        return Collections.unmodifiableList(resolutionHistory);
    }

    // Helper methods
    private static Instant calculateSLADeadline(Instant createdAt, ExceptionSeverity severity) {
        return switch (severity) {
            case CRITICAL -> createdAt.plus(Duration.ofHours(4));
            case HIGH -> createdAt.plus(Duration.ofHours(24));
            case MEDIUM -> createdAt.plus(Duration.ofDays(3));
            case LOW -> createdAt.plus(Duration.ofDays(7));
        };
    }

    private static EscalationLevel determineInitialEscalationLevel(ExceptionSeverity severity) {
        return switch (severity) {
            case CRITICAL -> EscalationLevel.L2_SPECIALIST;
            case HIGH -> EscalationLevel.L1_ANALYST;
            case MEDIUM, LOW -> EscalationLevel.L0_AUTOMATED;
        };
    }

    // Domain event management
    protected void addDomainEvent(DomainEvent event) {
        if (domainEvents == null) {
            domainEvents = new ArrayList<>();
        }
        domainEvents.add(event);
    }

    public List<DomainEvent> getDomainEventsAndClear() {
        List<DomainEvent> events = new ArrayList<>(domainEvents != null ? domainEvents : Collections.emptyList());
        if (domainEvents != null) {
            domainEvents.clear();
        }
        return events;
    }

    /**
     * Exception type enumeration.
     */
    public enum ExceptionType {
        EXTRACTION_FAILED,
        MATCHING_MISMATCH,
        COMPLIANCE_VIOLATION,
        PAYMENT_FAILED,
        SAP_INTEGRATION_ERROR,
        DATA_QUALITY_ISSUE,
        DUPLICATE_INVOICE,
        MISSING_REFERENCE
    }

    /**
     * Exception severity enumeration.
     */
    public enum ExceptionSeverity {
        LOW,        // Non-blocking, informational
        MEDIUM,     // Needs attention, not urgent
        HIGH,       // Urgent, blocking payment
        CRITICAL    // System critical, immediate action required
    }

    /**
     * Exception status enumeration.
     */
    public enum ExceptionStatus {
        OPEN,           // Created, awaiting triage
        ASSIGNED,       // Assigned to resolver
        IN_PROGRESS,    // Being worked on
        ESCALATED,      // Escalated to higher level
        RESOLVED,       // Successfully resolved
        CLOSED          // Closed (may be unresolved)
    }

    /**
     * Escalation level enumeration.
     */
    public enum EscalationLevel {
        L0_AUTOMATED,   // Automated resolution via ML
        L1_ANALYST,     // Level 1 analyst
        L2_SPECIALIST,  // Level 2 specialist
        L3_MANAGER,     // Manager escalation
        L4_EXECUTIVE    // Executive escalation
    }
}
