package com.invoicemanagement.sharedkernel.domain;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing a tenant identifier.
 * Immutable and used across all bounded contexts for multi-tenancy.
 *
 * DDD Pattern: Value Object (Identity)
 */
@Embeddable
@Getter
@EqualsAndHashCode
@ToString
public class TenantId implements Serializable {

    @NotNull
    private UUID id;

    /**
     * Protected no-arg constructor for JPA.
     */
    protected TenantId() {
    }

    private TenantId(UUID id) {
        Objects.requireNonNull(id, "Tenant ID cannot be null");
        this.id = id;
    }

    /**
     * Factory method to create TenantId from UUID.
     */
    public static TenantId of(UUID id) {
        return new TenantId(id);
    }

    /**
     * Factory method to create TenantId from string.
     */
    public static TenantId of(String id) {
        return new TenantId(UUID.fromString(id));
    }

    /**
     * Factory method to generate a new random TenantId.
     */
    public static TenantId generate() {
        return new TenantId(UUID.randomUUID());
    }

    /**
     * Get the string representation of the tenant ID.
     */
    public String asString() {
        return id.toString();
    }
}
