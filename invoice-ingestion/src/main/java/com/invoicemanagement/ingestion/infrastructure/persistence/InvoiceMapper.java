package com.invoicemanagement.ingestion.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicemanagement.ingestion.domain.*;
import com.invoicemanagement.sharedkernel.domain.AuditLog;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper between Invoice aggregate and InvoiceEntity.
 * Handles JSONB serialization/deserialization for complex value objects.
 *
 * Pattern: Data Mapper / Anti-Corruption Layer between domain and persistence
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceMapper {

    private final ObjectMapper objectMapper;

    /**
     * Map domain aggregate to entity for persistence.
     */
    public InvoiceEntity toEntity(Invoice invoice) {
        InvoiceEntity entity = new InvoiceEntity(invoice);

        try {
            // Serialize complex value objects to JSONB
            if (invoice.getVendorReference() != null) {
                entity.setVendorReference(objectMapper.writeValueAsString(invoice.getVendorReference()));
            }

            if (invoice.getInvoiceMetadata() != null) {
                entity.setInvoiceMetadata(objectMapper.writeValueAsString(invoice.getInvoiceMetadata()));
            }

            entity.setLineItems(objectMapper.writeValueAsString(invoice.getLineItems()));
            entity.setDocumentReference(objectMapper.writeValueAsString(invoice.getDocumentReference()));

            if (invoice.getExtractionResult() != null) {
                entity.setExtractionResult(objectMapper.writeValueAsString(invoice.getExtractionResult()));
            }

            entity.setAuditLog(objectMapper.writeValueAsString(invoice.getAuditLog()));

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize invoice to entity: {}", invoice.getInvoiceId(), e);
            throw new RuntimeException("Invoice serialization failed", e);
        }

        return entity;
    }

    /**
     * Map entity to domain aggregate for reconstitution.
     */
    public Invoice toDomain(InvoiceEntity entity) {
        try {
            // Deserialize JSONB to value objects
            VendorReference vendorReference = entity.getVendorReference() != null
                ? objectMapper.readValue(entity.getVendorReference(), VendorReference.class)
                : null;

            InvoiceMetadata invoiceMetadata = entity.getInvoiceMetadata() != null
                ? objectMapper.readValue(entity.getInvoiceMetadata(), InvoiceMetadata.class)
                : null;

            List<LineItem> lineItems = objectMapper.readValue(
                entity.getLineItems(),
                new TypeReference<List<LineItem>>() {}
            );

            DocumentReference documentReference = objectMapper.readValue(
                entity.getDocumentReference(),
                DocumentReference.class
            );

            ExtractionResult extractionResult = entity.getExtractionResult() != null
                ? objectMapper.readValue(entity.getExtractionResult(), ExtractionResult.class)
                : null;

            AuditLog auditLog = objectMapper.readValue(
                entity.getAuditLog(),
                AuditLog.class
            );

            // Reconstitute domain aggregate using reflection (or a reconstitution factory method)
            return Invoice.reconstitute(
                entity.getInvoiceId(),
                TenantId.of(entity.getTenantId()),
                vendorReference,
                invoiceMetadata,
                lineItems,
                documentReference,
                extractionResult,
                entity.getIngestionStatus(),
                auditLog,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
            );

        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize invoice from entity: {}", entity.getInvoiceId(), e);
            throw new RuntimeException("Invoice deserialization failed", e);
        }
    }
}
