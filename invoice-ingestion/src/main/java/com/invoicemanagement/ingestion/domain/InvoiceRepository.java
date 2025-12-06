package com.invoicemanagement.ingestion.domain;

import com.invoicemanagement.sharedkernel.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Invoice aggregate.
 * Defines domain operations for invoice persistence.
 *
 * DDD Pattern: Repository
 * Bounded Context: InvoiceIngestionContext
 */
public interface InvoiceRepository {

    /**
     * Save invoice aggregate (insert or update).
     *
     * @param invoice the invoice to save
     * @return the saved invoice
     */
    Invoice save(Invoice invoice);

    /**
     * Find invoice by ID within tenant context.
     *
     * @param invoiceId the invoice ID
     * @param tenantId the tenant ID
     * @return optional invoice
     */
    Optional<Invoice> findById(UUID invoiceId, TenantId tenantId);

    /**
     * Find invoice by ID (uses current tenant context).
     *
     * @param invoiceId the invoice ID
     * @return optional invoice
     */
    Optional<Invoice> findById(UUID invoiceId);

    /**
     * Check if invoice exists.
     *
     * @param invoiceId the invoice ID
     * @param tenantId the tenant ID
     * @return true if exists
     */
    boolean existsById(UUID invoiceId, TenantId tenantId);

    /**
     * Delete invoice (soft delete - changes status).
     *
     * @param invoice the invoice to delete
     */
    void delete(Invoice invoice);
}
