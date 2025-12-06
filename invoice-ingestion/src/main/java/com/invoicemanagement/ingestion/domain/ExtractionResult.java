package com.invoicemanagement.ingestion.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing the result of AI-powered document extraction.
 * Contains confidence scores, extracted fields, and validation errors.
 *
 * DDD Pattern: Entity (within Invoice aggregate)
 * Bounded Context: InvoiceIngestionContext
 */
@Getter
@EqualsAndHashCode(of = "extractionId")
@ToString
public class ExtractionResult implements Serializable {

    private final UUID extractionId;
    private final BigDecimal confidenceScore;
    private final ExtractionMethod extractionMethod;
    private final Map<String, FieldExtraction> extractedFields;
    private final List<ExtractionError> validationErrors;
    private final int retryCount;
    private final Instant extractedAt;

    private ExtractionResult(
        UUID extractionId,
        BigDecimal confidenceScore,
        ExtractionMethod extractionMethod,
        Map<String, FieldExtraction> extractedFields,
        List<ExtractionError> validationErrors,
        int retryCount,
        Instant extractedAt
    ) {
        this.extractionId = Objects.requireNonNull(extractionId, "Extraction ID cannot be null");
        this.confidenceScore = validateConfidenceScore(confidenceScore);
        this.extractionMethod = Objects.requireNonNull(extractionMethod, "Extraction method cannot be null");
        this.extractedFields = extractedFields != null ? Map.copyOf(extractedFields) : Map.of();
        this.validationErrors = validationErrors != null ? List.copyOf(validationErrors) : List.of();
        this.retryCount = Math.max(0, retryCount);
        this.extractedAt = Objects.requireNonNull(extractedAt, "Extracted timestamp cannot be null");
    }

    /**
     * Factory method for successful extraction.
     */
    public static ExtractionResult success(
        ExtractionMethod method,
        BigDecimal confidenceScore,
        Map<String, FieldExtraction> extractedFields
    ) {
        return new ExtractionResult(
            UUID.randomUUID(),
            confidenceScore,
            method,
            extractedFields,
            Collections.emptyList(),
            0,
            Instant.now()
        );
    }

    /**
     * Factory method for failed extraction with errors.
     */
    public static ExtractionResult withErrors(
        ExtractionMethod method,
        List<ExtractionError> errors,
        int retryCount
    ) {
        return new ExtractionResult(
            UUID.randomUUID(),
            BigDecimal.ZERO,
            method,
            Collections.emptyMap(),
            errors,
            retryCount,
            Instant.now()
        );
    }

    /**
     * Create a retry attempt (increments retry count).
     */
    public ExtractionResult retry() {
        return new ExtractionResult(
            UUID.randomUUID(),
            this.confidenceScore,
            this.extractionMethod,
            this.extractedFields,
            this.validationErrors,
            this.retryCount + 1,
            Instant.now()
        );
    }

    /**
     * Validate confidence score is between 0.0 and 1.0.
     */
    private BigDecimal validateConfidenceScore(BigDecimal score) {
        Objects.requireNonNull(score, "Confidence score cannot be null");
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Confidence score must be between 0.0 and 1.0");
        }
        return score;
    }

    /**
     * Check if extraction meets confidence threshold.
     */
    public boolean meetsConfidenceThreshold(BigDecimal threshold) {
        return confidenceScore.compareTo(threshold) >= 0;
    }

    /**
     * Check if extraction has errors.
     */
    public boolean hasErrors() {
        return !validationErrors.isEmpty();
    }

    /**
     * Check if max retries exceeded.
     */
    public boolean hasExceededMaxRetries(int maxRetries) {
        return retryCount >= maxRetries;
    }

    /**
     * Extraction method enumeration.
     */
    public enum ExtractionMethod {
        AZURE_OPENAI_GPT4,  // Azure OpenAI GPT-4 with vision
        OCR_TESSERACT,      // Tesseract OCR
        EDI_PARSER,         // EDI format parser
        XML_PARSER,         // XML parser
        EMAIL_NLP,          // Email NLP extraction
        MANUAL              // Manual data entry
    }

    /**
     * Individual field extraction with confidence.
     */
    @Getter
    @EqualsAndHashCode
    @ToString
    public static class FieldExtraction implements Serializable {
        private final String fieldName;
        private final String extractedValue;
        private final BigDecimal fieldConfidence;

        public FieldExtraction(String fieldName, String extractedValue, BigDecimal fieldConfidence) {
            this.fieldName = Objects.requireNonNull(fieldName, "Field name cannot be null");
            this.extractedValue = extractedValue;
            this.fieldConfidence = fieldConfidence;
        }

        public static FieldExtraction of(String fieldName, String value, BigDecimal confidence) {
            return new FieldExtraction(fieldName, value, confidence);
        }
    }

    /**
     * Extraction error.
     */
    @Getter
    @EqualsAndHashCode
    @ToString
    public static class ExtractionError implements Serializable {
        private final String errorCode;
        private final String errorMessage;
        private final String affectedField;

        public ExtractionError(String errorCode, String errorMessage, String affectedField) {
            this.errorCode = Objects.requireNonNull(errorCode, "Error code cannot be null");
            this.errorMessage = Objects.requireNonNull(errorMessage, "Error message cannot be null");
            this.affectedField = affectedField;
        }

        public static ExtractionError of(String code, String message) {
            return new ExtractionError(code, message, null);
        }

        public static ExtractionError of(String code, String message, String field) {
            return new ExtractionError(code, message, field);
        }
    }
}
