package com.invoicemanagement.sharedkernel.tenant;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * ThreadLocal-based tenant context holder.
 * Stores the current tenant for the executing thread to enable schema-based multi-tenancy.
 *
 * DDD Pattern: Infrastructure / Cross-Cutting Concern
 * Used for: Multi-tenant data isolation at database schema level
 */
@Slf4j
public class TenantContext {

    private static final ThreadLocal<TenantId> currentTenant = new ThreadLocal<>();

    private TenantContext() {
        // Utility class
    }

    /**
     * Set the current tenant for this thread.
     *
     * @param tenantId the tenant identifier
     */
    public static void setCurrentTenant(TenantId tenantId) {
        if (tenantId == null) {
            log.warn("Attempting to set null tenant ID");
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        log.debug("Setting current tenant: {}", tenantId);
        currentTenant.set(tenantId);
    }

    /**
     * Set the current tenant from UUID.
     */
    public static void setCurrentTenant(UUID tenantId) {
        setCurrentTenant(TenantId.of(tenantId));
    }

    /**
     * Set the current tenant from string.
     */
    public static void setCurrentTenant(String tenantId) {
        setCurrentTenant(TenantId.of(tenantId));
    }

    /**
     * Get the current tenant for this thread.
     *
     * @return current tenant ID
     * @throws IllegalStateException if no tenant is set
     */
    public static TenantId getCurrentTenant() {
        TenantId tenantId = currentTenant.get();
        if (tenantId == null) {
            log.error("No tenant set in current context");
            throw new IllegalStateException("No tenant context available. Tenant must be set before accessing tenant-specific data.");
        }
        return tenantId;
    }

    /**
     * Get the current tenant ID as UUID, or null if not set.
     */
    public static UUID getCurrentTenantIdOrNull() {
        TenantId tenantId = currentTenant.get();
        return tenantId != null ? tenantId.getId() : null;
    }

    /**
     * Get the current tenant ID as string, or null if not set.
     */
    public static String getCurrentTenantAsStringOrNull() {
        TenantId tenantId = currentTenant.get();
        return tenantId != null ? tenantId.asString() : null;
    }

    /**
     * Check if a tenant is currently set.
     */
    public static boolean isSet() {
        return currentTenant.get() != null;
    }

    /**
     * Clear the current tenant from this thread.
     * IMPORTANT: Must be called in finally block to prevent tenant leakage across requests.
     */
    public static void clear() {
        log.debug("Clearing tenant context");
        currentTenant.remove();
    }

    /**
     * Get the database schema name for the current tenant.
     * Convention: tenant_{tenantId}
     */
    public static String getCurrentTenantSchema() {
        TenantId tenantId = getCurrentTenant();
        return "tenant_" + tenantId.getId().toString().replace("-", "");
    }
}
