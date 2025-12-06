package com.invoicemanagement.sharedkernel.domain;

import java.util.Map;

/**
 * Helper class for creating audit log entries with simplified API.
 * Provides backward compatibility for aggregates using simplified audit logging.
 */
public class AuditLogHelper {

    /**
     * Create an AuditLog with a simple entry (system action).
     */
    public static AuditLog create() {
        return AuditLog.empty();
    }

    /**
     * Add a simple system audit entry.
     */
    public static AuditLog addEntry(
        AuditLog currentLog,
        String action,
        Object userId,
        String fieldName,
        String newValue,
        TenantId tenantId
    ) {
        AuditLog.AuditEntry entry = AuditLog.AuditEntry.builder()
            .action(mapAction(action))
            .userId(userId != null ? java.util.UUID.fromString(userId.toString()) : null)
            .changes(fieldName != null && newValue != null
                ? Map.of(fieldName, AuditLog.FieldChange.of(fieldName, null, newValue))
                : Map.of())
            .tenantId(tenantId)
            .build();

        return currentLog.addEntry(entry);
    }

    private static AuditLog.AuditAction mapAction(String action) {
        return switch (action.toUpperCase()) {
            case "TRANSACTION_CREATED", "EXCEPTION_CREATED", "CASE_CREATED" -> AuditLog.AuditAction.CREATED;
            case "MATCHING_COMPLETED", "COMPLIANCE_CHECK_ADDED", "ML_RECOMMENDATION_ADDED",
                 "RESOLUTION_STRATEGY_SET", "RESOLUTION_ACTION_TAKEN" -> AuditLog.AuditAction.UPDATED;
            case "VALIDATION_COMPLETED", "VALIDATION_FAILED" -> AuditLog.AuditAction.VALIDATED;
            case "CASE_ASSIGNED" -> AuditLog.AuditAction.UPDATED;
            case "CASE_ESCALATED" -> AuditLog.AuditAction.ESCALATED;
            case "CASE_RESOLVED" -> AuditLog.AuditAction.RESOLVED;
            case "SLA_BREACHED" -> AuditLog.AuditAction.UPDATED;
            default -> AuditLog.AuditAction.UPDATED;
        };
    }
}
