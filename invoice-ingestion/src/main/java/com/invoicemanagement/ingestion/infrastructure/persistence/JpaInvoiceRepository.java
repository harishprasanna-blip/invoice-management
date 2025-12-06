package com.invoicemanagement.ingestion.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for InvoiceEntity.
 * Provides database operations with multi-tenancy support.
 */
@Repository
public interface JpaInvoiceRepository extends JpaRepository<InvoiceEntity, UUID> {

    /**
     * Find invoice by ID and tenant ID.
     */
    @Query("SELECT i FROM InvoiceEntity i WHERE i.invoiceId = :invoiceId AND i.tenantId = :tenantId")
    Optional<InvoiceEntity> findByIdAndTenantId(
        @Param("invoiceId") UUID invoiceId,
        @Param("tenantId") UUID tenantId
    );

    /**
     * Check if invoice exists for tenant.
     */
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN TRUE ELSE FALSE END FROM InvoiceEntity i WHERE i.invoiceId = :invoiceId AND i.tenantId = :tenantId")
    boolean existsByIdAndTenantId(
        @Param("invoiceId") UUID invoiceId,
        @Param("tenantId") UUID tenantId
    );
}
