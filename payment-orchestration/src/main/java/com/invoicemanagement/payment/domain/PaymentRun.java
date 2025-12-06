package com.invoicemanagement.payment.domain;

import com.invoicemanagement.sharedkernel.domain.AuditLog;
import com.invoicemanagement.sharedkernel.domain.AuditLogHelper;
import com.invoicemanagement.sharedkernel.domain.Money;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.events.DomainEvent;
import com.invoicemanagement.payment.domain.events.PaymentApprovedEvent;
import com.invoicemanagement.payment.domain.events.PaymentExecutedEvent;
import com.invoicemanagement.payment.domain.events.PaymentFailedEvent;
import com.invoicemanagement.payment.domain.events.PaymentScheduledEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root implementing Saga Orchestrator pattern for payment processing.
 * Coordinates distributed transactions across ValidationContext, SAP ERP, and Payment Gateway.
 * 
 * Saga Pattern: Orchestration-based (not choreography)
 * Compensating Actions: Handles rollback on failure
 * 
 * DDD Pattern: Aggregate Root, Saga Orchestrator
 * Bounded Context: PaymentOrchestrationContext
 */
@Getter
@EqualsAndHashCode(of = "paymentRunId")
@ToString
public class PaymentRun implements Serializable {

    // Identity
    private final UUID paymentRunId;
    private final TenantId tenantId;

    // Payment details
    private final UUID invoiceId;
    private final String invoiceNumber;
    private final Money totalAmount;
    private final String vendorId;
    private final String vendorBankAccount;
    
    // Payment terms
    private final LocalDate dueDate;
    private final LocalDate scheduledPaymentDate;
    private PaymentMethod paymentMethod;
    
    // Saga orchestration state
    private PaymentStatus paymentStatus;
    private SagaState sagaState;
    private final List<SagaStep> completedSteps;
    private final List<CompensationAction> compensationActions;
    
    // SAP Integration
    private String sapDocumentNumber;
    private String sapPostingResult;
    
    // Payment Gateway
    private String gatewayTransactionId;
    private String gatewayResponse;
    
    // Approval workflow
    private String approvedBy;
    private Instant approvedAt;
    private String approvalNotes;
    
    // Execution tracking
    private Instant executedAt;
    private String failureReason;
    private int retryCount;
    
    // Audit trail
    private AuditLog auditLog;
    
    // Domain events
    private transient List<DomainEvent> domainEvents = new ArrayList<>();

    private PaymentRun(
        UUID paymentRunId,
        TenantId tenantId,
        UUID invoiceId,
        String invoiceNumber,
        Money totalAmount,
        String vendorId,
        String vendorBankAccount,
        LocalDate dueDate,
        LocalDate scheduledPaymentDate,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        SagaState sagaState,
        List<SagaStep> completedSteps,
        List<CompensationAction> compensationActions,
        String sapDocumentNumber,
        String sapPostingResult,
        String gatewayTransactionId,
        String gatewayResponse,
        String approvedBy,
        Instant approvedAt,
        String approvalNotes,
        Instant executedAt,
        String failureReason,
        int retryCount,
        AuditLog auditLog
    ) {
        this.paymentRunId = Objects.requireNonNull(paymentRunId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.invoiceId = Objects.requireNonNull(invoiceId);
        this.invoiceNumber = Objects.requireNonNull(invoiceNumber);
        this.totalAmount = Objects.requireNonNull(totalAmount);
        this.vendorId = Objects.requireNonNull(vendorId);
        this.vendorBankAccount = Objects.requireNonNull(vendorBankAccount);
        this.dueDate = Objects.requireNonNull(dueDate);
        this.scheduledPaymentDate = Objects.requireNonNull(scheduledPaymentDate);
        this.paymentMethod = Objects.requireNonNull(paymentMethod);
        this.paymentStatus = Objects.requireNonNull(paymentStatus);
        this.sagaState = Objects.requireNonNull(sagaState);
        this.completedSteps = new ArrayList<>(completedSteps);
        this.compensationActions = new ArrayList<>(compensationActions);
        this.sapDocumentNumber = sapDocumentNumber;
        this.sapPostingResult = sapPostingResult;
        this.gatewayTransactionId = gatewayTransactionId;
        this.gatewayResponse = gatewayResponse;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.approvalNotes = approvalNotes;
        this.executedAt = executedAt;
        this.failureReason = failureReason;
        this.retryCount = retryCount;
        this.auditLog = Objects.requireNonNull(auditLog);
    }

    /**
     * Factory method: Schedule a new payment run (starts Saga orchestration).
     */
    public static PaymentRun schedule(
        TenantId tenantId,
        UUID invoiceId,
        String invoiceNumber,
        Money totalAmount,
        String vendorId,
        String vendorBankAccount,
        LocalDate dueDate,
        PaymentMethod paymentMethod
    ) {
        UUID paymentRunId = UUID.randomUUID();
        LocalDate scheduledDate = calculateScheduledDate(dueDate, paymentMethod);

        AuditLog auditLog = AuditLogHelper.addEntry(
            AuditLog.empty(),
            "PAYMENT_SCHEDULED",
            null,
            "scheduledDate",
            scheduledDate.toString(),
            tenantId
        );

        PaymentRun paymentRun = new PaymentRun(
            paymentRunId,
            tenantId,
            invoiceId,
            invoiceNumber,
            totalAmount,
            vendorId,
            vendorBankAccount,
            dueDate,
            scheduledDate,
            paymentMethod,
            PaymentStatus.SCHEDULED,
            SagaState.STARTED,
            Collections.emptyList(),
            Collections.emptyList(),
            null, null, null, null,
            null, null, null,
            null, null, 0,
            auditLog
        );

        paymentRun.addDomainEvent(new PaymentScheduledEvent(
            tenantId,
            paymentRunId,
            invoiceId,
            totalAmount,
            scheduledDate
        ));

        return paymentRun;
    }

    /**
     * Factory method: Reconstitute from persistence.
     */
    public static PaymentRun reconstitute(
        UUID paymentRunId,
        TenantId tenantId,
        UUID invoiceId,
        String invoiceNumber,
        Money totalAmount,
        String vendorId,
        String vendorBankAccount,
        LocalDate dueDate,
        LocalDate scheduledPaymentDate,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        SagaState sagaState,
        List<SagaStep> completedSteps,
        List<CompensationAction> compensationActions,
        String sapDocumentNumber,
        String sapPostingResult,
        String gatewayTransactionId,
        String gatewayResponse,
        String approvedBy,
        Instant approvedAt,
        String approvalNotes,
        Instant executedAt,
        String failureReason,
        int retryCount,
        AuditLog auditLog
    ) {
        return new PaymentRun(
            paymentRunId, tenantId, invoiceId, invoiceNumber, totalAmount,
            vendorId, vendorBankAccount, dueDate, scheduledPaymentDate, paymentMethod,
            paymentStatus, sagaState, completedSteps, compensationActions,
            sapDocumentNumber, sapPostingResult, gatewayTransactionId, gatewayResponse,
            approvedBy, approvedAt, approvalNotes, executedAt, failureReason, retryCount,
            auditLog
        );
    }

    /**
     * Business Rule: Calculate scheduled payment date based on due date and payment method.
     */
    private static LocalDate calculateScheduledDate(LocalDate dueDate, PaymentMethod method) {
        return switch (method) {
            case ACH -> dueDate.minusDays(2);  // ACH takes 2 days
            case WIRE -> dueDate.minusDays(1);  // Wire is faster
            case CHECK -> dueDate.minusDays(5);  // Check mailing time
            case VIRTUAL_CARD -> dueDate;  // Instant
        };
    }

    /**
     * Saga Step 1: Approve payment (may require human approval based on amount).
     */
    public void approve(String approver, String notes) {
        if (this.paymentStatus != PaymentStatus.SCHEDULED) {
            throw new IllegalStateException("Can only approve scheduled payments");
        }

        this.paymentStatus = PaymentStatus.APPROVED;
        this.approvedBy = Objects.requireNonNull(approver);
        this.approvedAt = Instant.now();
        this.approvalNotes = notes;

        this.completedSteps.add(new SagaStep("APPROVAL", "COMPLETED", Instant.now(), null));
        this.sagaState = SagaState.IN_PROGRESS;

        this.auditLog = AuditLogHelper.addEntry(
            this.auditLog,
            "PAYMENT_APPROVED",
            approver,
            "approvalNotes",
            notes,
            this.tenantId
        );

        addDomainEvent(new PaymentApprovedEvent(
            tenantId,
            paymentRunId,
            invoiceId,
            approver,
            totalAmount
        ));
    }

    /**
     * Saga Step 2: Post to SAP accounting system.
     * Compensating action available if subsequent steps fail.
     */
    public void postToSAP(String documentNumber, String postingResult) {
        if (this.paymentStatus != PaymentStatus.APPROVED) {
            throw new IllegalStateException("Payment must be approved before SAP posting");
        }

        this.sapDocumentNumber = Objects.requireNonNull(documentNumber);
        this.sapPostingResult = postingResult;

        this.completedSteps.add(new SagaStep("SAP_POSTING", "COMPLETED", Instant.now(), documentNumber));
        
        // Register compensation action
        this.compensationActions.add(new CompensationAction(
            "REVERSE_SAP_POSTING",
            documentNumber,
            "POST /sap/api/accounting/documents/" + documentNumber + "/reverse"
        ));

        this.auditLog = AuditLogHelper.addEntry(
            this.auditLog,
            "SAP_POSTED",
            null,
            "sapDocumentNumber",
            documentNumber,
            this.tenantId
        );
    }

    /**
     * Saga Step 3: Execute payment via gateway.
     * Final step - if this succeeds, saga is complete.
     */
    public void executePayment(String transactionId, String gatewayResponse) {
        if (this.sapDocumentNumber == null) {
            throw new IllegalStateException("Must post to SAP before executing payment");
        }

        this.paymentStatus = PaymentStatus.EXECUTED;
        this.gatewayTransactionId = Objects.requireNonNull(transactionId);
        this.gatewayResponse = gatewayResponse;
        this.executedAt = Instant.now();
        this.sagaState = SagaState.COMPLETED;

        this.completedSteps.add(new SagaStep("PAYMENT_EXECUTION", "COMPLETED", Instant.now(), transactionId));

        this.auditLog = AuditLogHelper.addEntry(
            this.auditLog,
            "PAYMENT_EXECUTED",
            null,
            "gatewayTransactionId",
            transactionId,
            this.tenantId
        );

        addDomainEvent(new PaymentExecutedEvent(
            tenantId,
            paymentRunId,
            invoiceId,
            gatewayTransactionId,
            totalAmount,
            executedAt
        ));
    }

    /**
     * Saga Compensation: Handle failure and execute compensating actions.
     */
    public void fail(String reason, String failedStep) {
        this.paymentStatus = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.sagaState = SagaState.COMPENSATING;

        this.completedSteps.add(new SagaStep(failedStep, "FAILED", Instant.now(), reason));

        this.auditLog = AuditLogHelper.addEntry(
            this.auditLog,
            "PAYMENT_FAILED",
            null,
            "failureReason",
            reason,
            this.tenantId
        );

        addDomainEvent(new PaymentFailedEvent(
            tenantId,
            paymentRunId,
            invoiceId,
            reason,
            new ArrayList<>(compensationActions)
        ));
    }

    /**
     * Mark compensation as completed after rollback.
     */
    public void compensationCompleted() {
        if (this.sagaState != SagaState.COMPENSATING) {
            throw new IllegalStateException("Not in compensating state");
        }

        this.sagaState = SagaState.COMPENSATED;
        this.auditLog = AuditLogHelper.addEntry(
            this.auditLog,
            "COMPENSATION_COMPLETED",
            null,
            null,
            null,
            this.tenantId
        );
    }

    /**
     * Retry failed payment (with exponential backoff limits).
     */
    public void retry() {
        if (this.paymentStatus != PaymentStatus.FAILED) {
            throw new IllegalStateException("Can only retry failed payments");
        }

        if (this.retryCount >= 3) {
            throw new IllegalStateException("Maximum retry attempts reached");
        }

        this.retryCount++;
        this.paymentStatus = PaymentStatus.SCHEDULED;
        this.sagaState = SagaState.STARTED;
        this.failureReason = null;

        this.auditLog = AuditLogHelper.addEntry(
            this.auditLog,
            "PAYMENT_RETRY",
            null,
            "retryCount",
            String.valueOf(retryCount),
            this.tenantId
        );
    }

    /**
     * Check if payment requires manual approval based on amount threshold.
     */
    public boolean requiresApproval() {
        // Business rule: Payments over $10,000 require approval
        return this.totalAmount.getAmount().compareTo(java.math.BigDecimal.valueOf(10000)) > 0;
    }

    /**
     * Check if payment is overdue.
     */
    public boolean isOverdue() {
        return LocalDate.now().isAfter(this.dueDate) && 
               this.paymentStatus != PaymentStatus.EXECUTED;
    }

    // Domain event management
    private void addDomainEvent(DomainEvent event) {
        if (this.domainEvents == null) {
            this.domainEvents = new ArrayList<>();
        }
        this.domainEvents.add(event);
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents != null ? domainEvents : Collections.emptyList());
    }

    public void clearDomainEvents() {
        if (this.domainEvents != null) {
            this.domainEvents.clear();
        }
    }

    // Enum: Payment Status
    public enum PaymentStatus {
        SCHEDULED,    // Payment scheduled, waiting for approval
        APPROVED,     // Approved, ready for execution
        EXECUTED,     // Successfully executed
        FAILED,       // Failed (with compensation needed)
        CANCELLED     // Manually cancelled
    }

    // Enum: Saga State
    public enum SagaState {
        STARTED,      // Saga started
        IN_PROGRESS,  // Executing saga steps
        COMPLETED,    // All steps completed successfully
        COMPENSATING, // Executing compensating actions
        COMPENSATED   // Compensation completed
    }

    // Enum: Payment Method
    public enum PaymentMethod {
        ACH,           // Automated Clearing House
        WIRE,          // Wire transfer
        CHECK,         // Physical check
        VIRTUAL_CARD   // Virtual credit card
    }

    // Value Object: Saga Step
    @Getter
    @EqualsAndHashCode
    @ToString
    public static class SagaStep implements Serializable {
        private final String stepName;
        private final String status;
        private final Instant completedAt;
        private final String result;

        public SagaStep(String stepName, String status, Instant completedAt, String result) {
            this.stepName = stepName;
            this.status = status;
            this.completedAt = completedAt;
            this.result = result;
        }
    }

    // Value Object: Compensation Action
    @Getter
    @EqualsAndHashCode
    @ToString
    public static class CompensationAction implements Serializable {
        private final String actionName;
        private final String targetResource;
        private final String actionDetails;

        public CompensationAction(String actionName, String targetResource, String actionDetails) {
            this.actionName = actionName;
            this.targetResource = targetResource;
            this.actionDetails = actionDetails;
        }
    }
}
