package com.invoicemanagement.exceptionhandling.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.invoicemanagement.sharedkernel.domain.AuditLog;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.exceptionhandling.domain.ExceptionCase;
import com.invoicemanagement.exceptionhandling.domain.MLRecommendation;
import com.invoicemanagement.exceptionhandling.domain.ResolutionAction;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Mapper for translating between ExceptionCase aggregate and JPA entity.
 */
@Component
public class ExceptionCaseMapper {

    private final ObjectMapper objectMapper;

    public ExceptionCaseMapper() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public ExceptionCaseEntity toEntity(ExceptionCase exceptionCase) {
        ExceptionCaseEntity entity = new ExceptionCaseEntity();

        entity.setCaseId(exceptionCase.getCaseId());
        entity.setTenantId(exceptionCase.getTenantId().getId());
        entity.setExceptionType(exceptionCase.getExceptionType());
        entity.setSeverity(exceptionCase.getSeverity());
        entity.setSourceAggregateId(exceptionCase.getSourceAggregateId());
        entity.setSourceContext(exceptionCase.getSourceContext());
        entity.setExceptionDetails(exceptionCase.getExceptionDetails());
        entity.setStatus(exceptionCase.getStatus());
        entity.setResolutionStrategy(exceptionCase.getResolutionStrategy());
        entity.setResolutionNotes(exceptionCase.getResolutionNotes());
        entity.setResolvedBy(exceptionCase.getResolvedBy());
        entity.setResolvedAt(exceptionCase.getResolvedAt());
        entity.setEscalationLevel(exceptionCase.getEscalationLevel());
        entity.setAssignedTo(exceptionCase.getAssignedTo());
        entity.setAssignedAt(exceptionCase.getAssignedAt());
        entity.setCreatedAt(exceptionCase.getCreatedAt());
        entity.setSlaDeadline(exceptionCase.getSlaDeadline());
        entity.setSlaBreached(exceptionCase.isSlaBreached());

        try {
            entity.setMlRecommendations(objectMapper.writeValueAsString(exceptionCase.getMlRecommendations()));
            entity.setResolutionHistory(objectMapper.writeValueAsString(exceptionCase.getResolutionHistory()));
            entity.setAuditLog(objectMapper.writeValueAsString(exceptionCase.getAuditLog()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize exception case to JSON", e);
        }

        return entity;
    }

    public ExceptionCase toDomain(ExceptionCaseEntity entity) {
        try {
            List<MLRecommendation> mlRecommendations = objectMapper.readValue(
                entity.getMlRecommendations(),
                new TypeReference<List<MLRecommendation>>() {}
            );

            List<ResolutionAction> resolutionHistory = objectMapper.readValue(
                entity.getResolutionHistory(),
                new TypeReference<List<ResolutionAction>>() {}
            );

            AuditLog auditLog = objectMapper.readValue(
                entity.getAuditLog(),
                AuditLog.class
            );

            return ExceptionCase.reconstitute(
                entity.getCaseId(),
                TenantId.of(entity.getTenantId()),
                entity.getExceptionType(),
                entity.getSeverity(),
                entity.getSourceAggregateId(),
                entity.getSourceContext(),
                entity.getExceptionDetails(),
                entity.getStatus(),
                entity.getResolutionStrategy(),
                mlRecommendations != null ? mlRecommendations : Collections.emptyList(),
                resolutionHistory != null ? resolutionHistory : Collections.emptyList(),
                entity.getResolutionNotes(),
                entity.getResolvedBy(),
                entity.getResolvedAt(),
                entity.getEscalationLevel(),
                entity.getAssignedTo(),
                entity.getAssignedAt(),
                entity.getCreatedAt(),
                entity.getSlaDeadline(),
                entity.isSlaBreached(),
                auditLog
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize exception case from JSON", e);
        }
    }
}
