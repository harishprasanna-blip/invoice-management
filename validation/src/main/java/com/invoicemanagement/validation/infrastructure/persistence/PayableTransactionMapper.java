package com.invoicemanagement.validation.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.invoicemanagement.sharedkernel.domain.AuditLog;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.validation.domain.*;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Mapper for translating between PayableTransaction aggregate and JPA entity.
 * Handles JSON serialization/deserialization of complex value objects.
 */
@Component
public class PayableTransactionMapper {

    private final ObjectMapper objectMapper;

    public PayableTransactionMapper() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Convert domain aggregate to JPA entity.
     */
    public PayableTransactionEntity toEntity(PayableTransaction transaction) {
        PayableTransactionEntity entity = new PayableTransactionEntity();

        entity.setTransactionId(transaction.getTransactionId());
        entity.setTenantId(transaction.getTenantId().getId());
        entity.setInvoiceId(transaction.getInvoiceReference().getInvoiceId());
        entity.setValidationStatus(transaction.getValidationStatus());
        entity.setValidatedAt(transaction.getValidatedAt());

        try {
            // Serialize complex value objects to JSON
            entity.setInvoiceReference(objectMapper.writeValueAsString(transaction.getInvoiceReference()));

            if (transaction.getPurchaseOrderReference() != null) {
                entity.setPoReference(objectMapper.writeValueAsString(transaction.getPurchaseOrderReference()));
            }

            if (transaction.getGoodsReceiptReference() != null) {
                entity.setGrReference(objectMapper.writeValueAsString(transaction.getGoodsReceiptReference()));
            }

            if (transaction.getMatchingResult() != null) {
                entity.setMatchingResult(objectMapper.writeValueAsString(transaction.getMatchingResult()));
            }

            entity.setComplianceChecks(objectMapper.writeValueAsString(transaction.getComplianceChecks()));
            entity.setAuditLog(objectMapper.writeValueAsString(transaction.getAuditLog()));

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize transaction to JSON", e);
        }

        return entity;
    }

    /**
     * Convert JPA entity to domain aggregate (reconstitution).
     */
    public PayableTransaction toDomain(PayableTransactionEntity entity) {
        try {
            // Deserialize complex value objects from JSON
            PayableTransaction.InvoiceReference invoiceReference = objectMapper.readValue(
                entity.getInvoiceReference(),
                PayableTransaction.InvoiceReference.class
            );

            PurchaseOrderReference poReference = entity.getPoReference() != null
                ? objectMapper.readValue(entity.getPoReference(), PurchaseOrderReference.class)
                : null;

            GoodsReceiptReference grReference = entity.getGrReference() != null
                ? objectMapper.readValue(entity.getGrReference(), GoodsReceiptReference.class)
                : null;

            MatchingResult matchingResult = entity.getMatchingResult() != null
                ? objectMapper.readValue(entity.getMatchingResult(), MatchingResult.class)
                : null;

            List<ComplianceCheck> complianceChecks = objectMapper.readValue(
                entity.getComplianceChecks(),
                new TypeReference<List<ComplianceCheck>>() {}
            );

            AuditLog auditLog = objectMapper.readValue(
                entity.getAuditLog(),
                AuditLog.class
            );

            // Reconstitute aggregate (does not emit domain events)
            return PayableTransaction.reconstitute(
                entity.getTransactionId(),
                TenantId.of(entity.getTenantId()),
                invoiceReference,
                poReference,
                grReference,
                matchingResult,
                complianceChecks != null ? complianceChecks : Collections.emptyList(),
                entity.getValidationStatus(),
                entity.getValidatedAt(),
                auditLog
            );

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize transaction from JSON", e);
        }
    }
}
