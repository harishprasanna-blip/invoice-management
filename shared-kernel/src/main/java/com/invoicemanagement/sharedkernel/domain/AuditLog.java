package com.invoicemanagement.sharedkernel.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing an immutable audit trail for domain aggregates.
 * Stores who, when, and what changed for compliance (SOC2, GDPR).
 *
 * DDD Pattern: Value Object
 * Compliance: SOC2 (audit logging), GDPR (data processing transparency)
 */
@Getter
@EqualsAndHashCode
@ToString
public class AuditLog implements Serializable {

    private final List<AuditEntry> entries;

    private AuditLog() {
        this.entries = new ArrayList<>();
    }

    private AuditLog(List<AuditEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    /**
     * Create an empty audit log.
     */
    public static AuditLog empty() {
        return new AuditLog();
    }

    /**
     * Add a new audit entry (returns new instance - immutability).
     */
    public AuditLog addEntry(AuditEntry entry) {
        List<AuditEntry> newEntries = new ArrayList<>(this.entries);
        newEntries.add(entry);
        return new AuditLog(newEntries);
    }

    /**
     * Get immutable list of entries.
     */
    public List<AuditEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Get the latest N entries (for embedding in aggregate).
     */
    public List<AuditEntry> getLatestEntries(int count) {
        int size = entries.size();
        int fromIndex = Math.max(0, size - count);
        return Collections.unmodifiableList(entries.subList(fromIndex, size));
    }

    /**
     * Individual audit entry.
     */
    @Getter
    @EqualsAndHashCode
    @ToString
    public static class AuditEntry implements Serializable {

        private final Instant timestamp;
        private final AuditAction action;
        private final UUID userId;
        private final String role;
        private final Map<String, FieldChange> changes;
        private final String ipAddress;
        private final UUID correlationId;
        private final TenantId tenantId;

        @JsonCreator
        public AuditEntry(
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("action") AuditAction action,
            @JsonProperty("userId") UUID userId,
            @JsonProperty("role") String role,
            @JsonProperty("changes") Map<String, FieldChange> changes,
            @JsonProperty("ipAddress") String ipAddress,
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("tenantId") TenantId tenantId
        ) {
            this.timestamp = Objects.requireNonNull(timestamp, "Timestamp cannot be null");
            this.action = Objects.requireNonNull(action, "Action cannot be null");
            this.userId = userId; // Can be null for system actions
            this.role = role;
            this.changes = changes != null ? Map.copyOf(changes) : Map.of();
            this.ipAddress = ipAddress;
            this.correlationId = correlationId;
            this.tenantId = Objects.requireNonNull(tenantId, "TenantId cannot be null");
        }

        /**
         * Builder for fluent creation.
         */
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Instant timestamp = Instant.now();
            private AuditAction action;
            private UUID userId;
            private String role;
            private Map<String, FieldChange> changes = Map.of();
            private String ipAddress;
            private UUID correlationId = UUID.randomUUID();
            private TenantId tenantId;

            public Builder timestamp(Instant timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public Builder action(AuditAction action) {
                this.action = action;
                return this;
            }

            public Builder userId(UUID userId) {
                this.userId = userId;
                return this;
            }

            public Builder role(String role) {
                this.role = role;
                return this;
            }

            public Builder changes(Map<String, FieldChange> changes) {
                this.changes = changes;
                return this;
            }

            public Builder ipAddress(String ipAddress) {
                this.ipAddress = ipAddress;
                return this;
            }

            public Builder correlationId(UUID correlationId) {
                this.correlationId = correlationId;
                return this;
            }

            public Builder tenantId(TenantId tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public AuditEntry build() {
                return new AuditEntry(timestamp, action, userId, role, changes, ipAddress, correlationId, tenantId);
            }
        }
    }

    /**
     * Represents a field-level change for audit.
     */
    @Getter
    @EqualsAndHashCode
    @ToString
    public static class FieldChange implements Serializable {
        private final String fieldName;
        private final String oldValue;
        private final String newValue;
        private final String changeReason;

        @JsonCreator
        public FieldChange(
            @JsonProperty("fieldName") String fieldName,
            @JsonProperty("oldValue") String oldValue,
            @JsonProperty("newValue") String newValue,
            @JsonProperty("changeReason") String changeReason
        ) {
            this.fieldName = Objects.requireNonNull(fieldName, "Field name cannot be null");
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.changeReason = changeReason;
        }

        public static FieldChange of(String fieldName, String oldValue, String newValue) {
            return new FieldChange(fieldName, oldValue, newValue, null);
        }

        public static FieldChange of(String fieldName, String oldValue, String newValue, String reason) {
            return new FieldChange(fieldName, oldValue, newValue, reason);
        }
    }

    /**
     * Audit action types.
     */
    public enum AuditAction {
        CREATED,
        UPDATED,
        DELETED,
        APPROVED,
        REJECTED,
        EXTRACTED,
        VALIDATED,
        POSTED,
        ESCALATED,
        RESOLVED,
        EXECUTED
    }
}
