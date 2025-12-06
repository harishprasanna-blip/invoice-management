package com.invoicemanagement.payment.infrastructure.persistence;

import com.invoicemanagement.payment.domain.PaymentRun;
import com.invoicemanagement.sharedkernel.domain.AuditLog;
import com.invoicemanagement.sharedkernel.domain.Money;
import com.invoicemanagement.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper between PaymentRun aggregate and JPA entity.
 */
public class PaymentRunMapper {

    public static PaymentRunEntity toEntity(PaymentRun paymentRun) {
        PaymentRunEntity entity = new PaymentRunEntity();
        
        entity.setPaymentRunId(paymentRun.getPaymentRunId());
        entity.setTenantId(paymentRun.getTenantId().getId().toString());
        entity.setInvoiceId(paymentRun.getInvoiceId());
        entity.setInvoiceNumber(paymentRun.getInvoiceNumber());
        entity.setTotalAmountValue(paymentRun.getTotalAmount().getAmount());
        entity.setTotalAmountCurrency(paymentRun.getTotalAmount().getCurrencyCode());
        entity.setVendorId(paymentRun.getVendorId());
        entity.setVendorBankAccount(paymentRun.getVendorBankAccount());
        entity.setDueDate(paymentRun.getDueDate());
        entity.setScheduledPaymentDate(paymentRun.getScheduledPaymentDate());
        entity.setPaymentMethod(paymentRun.getPaymentMethod().name());
        entity.setPaymentStatus(paymentRun.getPaymentStatus().name());
        entity.setSagaState(paymentRun.getSagaState().name());
        
        // Saga steps as JSON
        entity.setCompletedSteps(paymentRun.getCompletedSteps().stream()
            .map(PaymentRunMapper::sagaStepToJson)
            .collect(Collectors.toList()));
        
        entity.setCompensationActions(paymentRun.getCompensationActions().stream()
            .map(PaymentRunMapper::compensationToJson)
            .collect(Collectors.toList()));
        
        entity.setSapDocumentNumber(paymentRun.getSapDocumentNumber());
        entity.setSapPostingResult(paymentRun.getSapPostingResult());
        entity.setGatewayTransactionId(paymentRun.getGatewayTransactionId());
        entity.setGatewayResponse(paymentRun.getGatewayResponse());
        entity.setApprovedBy(paymentRun.getApprovedBy());
        entity.setApprovedAt(paymentRun.getApprovedAt());
        entity.setApprovalNotes(paymentRun.getApprovalNotes());
        entity.setExecutedAt(paymentRun.getExecutedAt());
        entity.setFailureReason(paymentRun.getFailureReason());
        entity.setRetryCount(paymentRun.getRetryCount());
        
        // Audit log as JSON
        entity.setAuditLogJson(auditLogToJson(paymentRun.getAuditLog()));
        
        return entity;
    }

    public static PaymentRun toDomain(PaymentRunEntity entity) {
        TenantId tenantId = TenantId.of(UUID.fromString(entity.getTenantId()));
        Money totalAmount = Money.of(entity.getTotalAmountValue(), entity.getTotalAmountCurrency());
        
        List<PaymentRun.SagaStep> steps = entity.getCompletedSteps() != null
            ? entity.getCompletedSteps().stream()
                .map(PaymentRunMapper::jsonToSagaStep)
                .collect(Collectors.toList())
            : new ArrayList<>();
        
        List<PaymentRun.CompensationAction> compensations = entity.getCompensationActions() != null
            ? entity.getCompensationActions().stream()
                .map(PaymentRunMapper::jsonToCompensation)
                .collect(Collectors.toList())
            : new ArrayList<>();
        
        AuditLog auditLog = jsonToAuditLog(entity.getAuditLogJson());
        
        return PaymentRun.reconstitute(
            entity.getPaymentRunId(),
            tenantId,
            entity.getInvoiceId(),
            entity.getInvoiceNumber(),
            totalAmount,
            entity.getVendorId(),
            entity.getVendorBankAccount(),
            entity.getDueDate(),
            entity.getScheduledPaymentDate(),
            PaymentRun.PaymentMethod.valueOf(entity.getPaymentMethod()),
            PaymentRun.PaymentStatus.valueOf(entity.getPaymentStatus()),
            PaymentRun.SagaState.valueOf(entity.getSagaState()),
            steps,
            compensations,
            entity.getSapDocumentNumber(),
            entity.getSapPostingResult(),
            entity.getGatewayTransactionId(),
            entity.getGatewayResponse(),
            entity.getApprovedBy(),
            entity.getApprovedAt(),
            entity.getApprovalNotes(),
            entity.getExecutedAt(),
            entity.getFailureReason(),
            entity.getRetryCount(),
            auditLog
        );
    }

    private static SagaStepJson sagaStepToJson(PaymentRun.SagaStep step) {
        SagaStepJson json = new SagaStepJson();
        json.stepName = step.getStepName();
        json.status = step.getStatus();
        json.completedAt = step.getCompletedAt();
        json.result = step.getResult();
        return json;
    }

    private static PaymentRun.SagaStep jsonToSagaStep(SagaStepJson json) {
        return new PaymentRun.SagaStep(
            json.stepName,
            json.status,
            json.completedAt,
            json.result
        );
    }

    private static CompensationJson compensationToJson(PaymentRun.CompensationAction action) {
        CompensationJson json = new CompensationJson();
        json.actionName = action.getActionName();
        json.targetResource = action.getTargetResource();
        json.actionDetails = action.getActionDetails();
        return json;
    }

    private static PaymentRun.CompensationAction jsonToCompensation(CompensationJson json) {
        return new PaymentRun.CompensationAction(
            json.actionName,
            json.targetResource,
            json.actionDetails
        );
    }

    private static AuditLogJson auditLogToJson(AuditLog auditLog) {
        AuditLogJson json = new AuditLogJson();
        json.entries = auditLog.getEntries().stream()
            .map(entry -> {
                AuditEntryJson entryJson = new AuditEntryJson();
                entryJson.timestamp = entry.getTimestamp();
                entryJson.action = entry.getAction().name();
                entryJson.userId = entry.getUserId() != null ? entry.getUserId().toString() : null;
                entryJson.role = entry.getRole();
                entryJson.ipAddress = entry.getIpAddress();
                entryJson.correlationId = entry.getCorrelationId() != null ? entry.getCorrelationId().toString() : null;
                entryJson.tenantId = entry.getTenantId().getId().toString();
                
                // Serialize changes map
                if (entry.getChanges() != null && !entry.getChanges().isEmpty()) {
                    entryJson.changes = entry.getChanges().entrySet().stream()
                        .map(changeEntry -> {
                            FieldChangeJson changeJson = new FieldChangeJson();
                            changeJson.fieldName = changeEntry.getValue().getFieldName();
                            changeJson.oldValue = changeEntry.getValue().getOldValue();
                            changeJson.newValue = changeEntry.getValue().getNewValue();
                            changeJson.changeReason = changeEntry.getValue().getChangeReason();
                            return changeJson;
                        })
                        .collect(Collectors.toList());
                }
                
                return entryJson;
            })
            .collect(Collectors.toList());
        return json;
    }

    private static AuditLog jsonToAuditLog(AuditLogJson json) {
        if (json == null || json.entries == null || json.entries.isEmpty()) {
            return AuditLog.empty();
        }

        AuditLog auditLog = AuditLog.empty();
        
        for (AuditEntryJson entryJson : json.entries) {
            // Reconstruct changes map
            java.util.Map<String, AuditLog.FieldChange> changes = new java.util.HashMap<>();
            if (entryJson.changes != null) {
                for (FieldChangeJson changeJson : entryJson.changes) {
                    AuditLog.FieldChange fieldChange = new AuditLog.FieldChange(
                        changeJson.fieldName,
                        changeJson.oldValue,
                        changeJson.newValue,
                        changeJson.changeReason
                    );
                    changes.put(changeJson.fieldName, fieldChange);
                }
            }
            
            AuditLog.AuditEntry entry = AuditLog.AuditEntry.builder()
                .timestamp(entryJson.timestamp)
                .action(AuditLog.AuditAction.valueOf(entryJson.action))
                .userId(entryJson.userId != null ? UUID.fromString(entryJson.userId) : null)
                .role(entryJson.role)
                .changes(changes)
                .ipAddress(entryJson.ipAddress)
                .correlationId(entryJson.correlationId != null ? UUID.fromString(entryJson.correlationId) : null)
                .tenantId(TenantId.of(UUID.fromString(entryJson.tenantId)))
                .build();
            
            auditLog = auditLog.addEntry(entry);
        }

        return auditLog;
    }

    // JSON DTOs for JSONB serialization
    public static class SagaStepJson {
        public String stepName;
        public String status;
        public Instant completedAt;
        public String result;
    }

    public static class CompensationJson {
        public String actionName;
        public String targetResource;
        public String actionDetails;
    }

    public static class AuditLogJson {
        public List<AuditEntryJson> entries;
    }

    public static class AuditEntryJson {
        public Instant timestamp;
        public String action;
        public String userId;
        public String role;
        public String ipAddress;
        public String correlationId;
        public String tenantId;
        public List<FieldChangeJson> changes;
    }
    
    public static class FieldChangeJson {
        public String fieldName;
        public String oldValue;
        public String newValue;
        public String changeReason;
    }
}
