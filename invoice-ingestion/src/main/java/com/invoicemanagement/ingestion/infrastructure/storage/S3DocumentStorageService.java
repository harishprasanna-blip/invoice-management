package com.invoicemanagement.ingestion.infrastructure.storage;

import com.invoicemanagement.ingestion.domain.DocumentReference;
import com.invoicemanagement.ingestion.domain.DocumentReference.DocumentFormat;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AWS S3 implementation of DocumentStorageService.
 * Handles invoice document storage with encryption, versioning, and lifecycle.
 *
 * Pattern: Adapter (implements Port interface)
 * Security: Server-side encryption (SSE-S3), presigned URLs for temporary access
 * Compliance: 7-year retention, encryption at rest (SOC2/GDPR)
 */
@Service
@Slf4j
public class S3DocumentStorageService implements DocumentStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;

    public S3DocumentStorageService(
        S3Client s3Client,
        S3Presigner s3Presigner,
        @Value("${aws.s3.bucket-name}") String bucketName
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
    }

    @Override
    public DocumentReference uploadDocument(
        TenantId tenantId,
        UUID invoiceId,
        String fileName,
        String contentType,
        InputStream inputStream,
        long contentLength
    ) {
        // Generate S3 key with tenant isolation
        String s3Key = generateS3Key(tenantId, invoiceId, fileName);

        // Prepare metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("tenant-id", tenantId.asString());
        metadata.put("invoice-id", invoiceId.toString());
        metadata.put("original-filename", fileName);
        metadata.put("upload-timestamp", Instant.now().toString());

        try {
            // Upload to S3 with server-side encryption
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(contentType)
                .contentLength(contentLength)
                .metadata(metadata)
                .serverSideEncryption(ServerSideEncryption.AES256) // SSE-S3 encryption
                .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));

            log.info("Document uploaded to S3: bucket={}, key={}, size={} bytes",
                bucketName, s3Key, contentLength);

            // Determine document format from content type
            DocumentFormat format = determineDocumentFormat(fileName, contentType);

            // Create and return document reference
            return DocumentReference.of(s3Key, fileName, format);

        } catch (S3Exception e) {
            log.error("Failed to upload document to S3: key={}", s3Key, e);
            throw new DocumentStorageException("Failed to upload document", e);
        }
    }

    @Override
    public InputStream downloadDocument(DocumentReference documentReference) {
        String s3Key = documentReference.getS3BucketKey();

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

            InputStream inputStream = s3Client.getObject(getObjectRequest);

            log.info("Document downloaded from S3: bucket={}, key={}", bucketName, s3Key);

            return inputStream;

        } catch (NoSuchKeyException e) {
            log.error("Document not found in S3: key={}", s3Key);
            throw new DocumentNotFoundException("Document not found: " + s3Key, e);
        } catch (S3Exception e) {
            log.error("Failed to download document from S3: key={}", s3Key, e);
            throw new DocumentStorageException("Failed to download document", e);
        }
    }

    @Override
    public void deleteDocument(DocumentReference documentReference) {
        String s3Key = documentReference.getS3BucketKey();

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

            s3Client.deleteObject(deleteObjectRequest);

            log.info("Document deleted from S3: bucket={}, key={}", bucketName, s3Key);

        } catch (S3Exception e) {
            log.error("Failed to delete document from S3: key={}", s3Key, e);
            throw new DocumentStorageException("Failed to delete document", e);
        }
    }

    @Override
    public boolean exists(DocumentReference documentReference) {
        String s3Key = documentReference.getS3BucketKey();

        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

            s3Client.headObject(headObjectRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.error("Failed to check document existence in S3: key={}", s3Key, e);
            throw new DocumentStorageException("Failed to check document existence", e);
        }
    }

    @Override
    public String getPresignedUrl(DocumentReference documentReference, int expirationMinutes) {
        String s3Key = documentReference.getS3BucketKey();

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expirationMinutes))
                .getObjectRequest(getObjectRequest)
                .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            String url = presignedRequest.url().toString();

            log.info("Generated presigned URL for document: key={}, expiration={}min", s3Key, expirationMinutes);

            return url;

        } catch (S3Exception e) {
            log.error("Failed to generate presigned URL: key={}", s3Key, e);
            throw new DocumentStorageException("Failed to generate presigned URL", e);
        }
    }

    /**
     * Generate S3 key with tenant isolation.
     * Format: tenants/{tenantId}/invoices/{invoiceId}/{filename}
     */
    private String generateS3Key(TenantId tenantId, UUID invoiceId, String fileName) {
        // Sanitize filename to prevent path traversal
        String sanitizedFileName = sanitizeFileName(fileName);

        return String.format("tenants/%s/invoices/%s/%s",
            tenantId.asString(),
            invoiceId.toString(),
            sanitizedFileName
        );
    }

    /**
     * Sanitize filename to prevent security issues.
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document_" + UUID.randomUUID();
        }

        // Remove path separators and dangerous characters
        return fileName.replaceAll("[/\\\\]", "_")
            .replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Determine document format from file extension and content type.
     */
    private DocumentFormat determineDocumentFormat(String fileName, String contentType) {
        String lowerFileName = fileName.toLowerCase();

        if (lowerFileName.endsWith(".pdf") || "application/pdf".equals(contentType)) {
            return DocumentFormat.PDF;
        } else if (lowerFileName.endsWith(".xml")) {
            return DocumentFormat.XML;
        } else if (lowerFileName.endsWith(".edi") || lowerFileName.endsWith(".x12")) {
            return DocumentFormat.EDI_X12;
        } else if (lowerFileName.endsWith(".edifact")) {
            return DocumentFormat.EDIFACT;
        } else if (lowerFileName.endsWith(".eml") || "message/rfc822".equals(contentType)) {
            return DocumentFormat.EMAIL;
        } else if (contentType != null && contentType.startsWith("image/")) {
            return DocumentFormat.IMAGE;
        } else {
            return DocumentFormat.PDF; // Default
        }
    }

    /**
     * Custom exception for storage errors.
     */
    public static class DocumentStorageException extends RuntimeException {
        public DocumentStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Custom exception for not found errors.
     */
    public static class DocumentNotFoundException extends RuntimeException {
        public DocumentNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
