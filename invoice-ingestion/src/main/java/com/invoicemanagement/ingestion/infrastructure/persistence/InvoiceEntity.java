package com.invoicemanagement.ingestion.infrastructure.persistence;

import com.invoicemanagement.ingestion.domain.Invoice;
import com.invoicemanagement.ingestion.domain.Invoice.IngestionStatus;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for Invoice persistence.
 * Maps Invoice aggregate to PostgreSQL with JSONB columns for complex value objects.
 *
 * Pattern: Aggregate persistence with JSONB for flexibility
 * Schema: Tenant-specific schema (tenant_{tenantId}.invoices)
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceEntity {

    @Id
    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vendor_reference", nullable = false, columnDefinition = "jsonb")
    private String vendorReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "invoice_metadata", columnDefinition = "jsonb")
    private String invoiceMetadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "line_items", nullable = false, columnDefinition = "jsonb")
    private String lineItems;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document_reference", nullable = false, columnDefinition = "jsonb")
    private String documentReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extraction_result", columnDefinition = "jsonb")
    private String extractionResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_status", nullable = false, length = 50)
    private IngestionStatus ingestionStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audit_log", nullable = false, columnDefinition = "jsonb")
    private String auditLog;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Constructor from domain aggregate.
     */
    public InvoiceEntity(Invoice invoice) {
        this.invoiceId = invoice.getInvoiceId();
        this.tenantId = invoice.getTenantId().getId();
        this.ingestionStatus = invoice.getIngestionStatus();
        this.createdAt = invoice.getCreatedAt();
        this.updatedAt = invoice.getUpdatedAt();
        this.version = invoice.getVersion();
    }

    /**
     * Get TenantId value object.
     */
    public TenantId getTenantIdValue() {
        return TenantId.of(tenantId);
    }
}
