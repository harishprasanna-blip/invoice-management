package com.invoicemanagement.ingestion.infrastructure.storage;

import com.invoicemanagement.ingestion.domain.DocumentReference;
import com.invoicemanagement.sharedkernel.domain.TenantId;

import java.io.InputStream;
import java.util.UUID;

/**
 * Interface for document storage operations.
 * Abstracts storage implementation (S3, Azure Blob, etc.) from domain.
 *
 * Pattern: Port (Hexagonal Architecture)
 * Bounded Context: InvoiceIngestionContext
 */
public interface DocumentStorageService {

    /**
     * Upload document to storage.
     *
     * @param tenantId tenant identifier
     * @param invoiceId invoice identifier
     * @param fileName original file name
     * @param contentType MIME type
     * @param inputStream document content
     * @param contentLength size in bytes
     * @return document reference with storage location
     */
    DocumentReference uploadDocument(
        TenantId tenantId,
        UUID invoiceId,
        String fileName,
        String contentType,
        InputStream inputStream,
        long contentLength
    );

    /**
     * Download document from storage.
     *
     * @param documentReference reference to document
     * @return document content as input stream
     */
    InputStream downloadDocument(DocumentReference documentReference);

    /**
     * Delete document from storage.
     *
     * @param documentReference reference to document
     */
    void deleteDocument(DocumentReference documentReference);

    /**
     * Check if document exists.
     *
     * @param documentReference reference to document
     * @return true if exists
     */
    boolean exists(DocumentReference documentReference);

    /**
     * Get presigned URL for temporary access (for Python AI service).
     *
     * @param documentReference reference to document
     * @param expirationMinutes URL expiration time
     * @return presigned URL
     */
    String getPresignedUrl(DocumentReference documentReference, int expirationMinutes);
}
