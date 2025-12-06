package com.invoicemanagement.ingestion.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Value Object referencing the original invoice document in S3.
 * Immutable pointer to document storage.
 *
 * DDD Pattern: Value Object
 * Bounded Context: InvoiceIngestionContext
 */
@Getter
@EqualsAndHashCode
@ToString
public class DocumentReference implements Serializable {

    private final String s3BucketKey;
    private final String originalFileName;
    private final DocumentFormat documentFormat;
    private final Instant uploadTimestamp;

    @JsonCreator
    public DocumentReference(
        @JsonProperty("s3BucketKey") String s3BucketKey,
        @JsonProperty("originalFileName") String originalFileName,
        @JsonProperty("documentFormat") DocumentFormat documentFormat,
        @JsonProperty("uploadTimestamp") Instant uploadTimestamp
    ) {
        this.s3BucketKey = Objects.requireNonNull(s3BucketKey, "S3 bucket key cannot be null");
        this.originalFileName = Objects.requireNonNull(originalFileName, "Original file name cannot be null");
        this.documentFormat = Objects.requireNonNull(documentFormat, "Document format cannot be null");
        this.uploadTimestamp = Objects.requireNonNull(uploadTimestamp, "Upload timestamp cannot be null");
    }

    public static DocumentReference of(String s3BucketKey, String originalFileName, DocumentFormat format) {
        return new DocumentReference(s3BucketKey, originalFileName, format, Instant.now());
    }

    /**
     * Document format enumeration.
     */
    public enum DocumentFormat {
        PDF,          // PDF documents
        EDI_X12,      // EDI X12 format (North America)
        EDIFACT,      // EDIFACT format (International)
        EMAIL,        // Email-based invoices
        XML,          // XML invoices (e.g., UBL, CII)
        IMAGE         // Scanned images (JPEG, PNG)
    }

    /**
     * Check if format requires OCR.
     */
    public boolean requiresOcr() {
        return documentFormat == DocumentFormat.PDF || documentFormat == DocumentFormat.IMAGE;
    }

    /**
     * Check if format is structured (EDI/XML).
     */
    public boolean isStructuredFormat() {
        return documentFormat == DocumentFormat.EDI_X12 ||
               documentFormat == DocumentFormat.EDIFACT ||
               documentFormat == DocumentFormat.XML;
    }
}
