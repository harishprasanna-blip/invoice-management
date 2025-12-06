package com.invoicemanagement.ingestion.application;

import com.invoicemanagement.ingestion.domain.*;
import com.invoicemanagement.ingestion.infrastructure.storage.DocumentStorageService;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Application Service for invoice ingestion workflow.
 * Orchestrates use cases across domain, storage, and AI extraction.
 *
 * Pattern: Application Service
 * Responsibilities:
 * - Coordinate invoice upload and extraction workflow
 * - Delegate to domain aggregates and infrastructure services
 * - Handle transactions and error recovery
 *
 * Bounded Context: InvoiceIngestionContext
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceIngestionService {

    private final InvoiceRepository invoiceRepository;
    private final DocumentStorageService documentStorageService;
    private final ExtractionServiceClient extractionServiceClient;

    /**
     * Submit invoice document for processing.
     * Use case: Upload document → Store in S3 → Trigger AI extraction
     *
     * @param file uploaded document file
     * @param vendorHint optional vendor information from user
     * @return created invoice ID
     */
    @Transactional
    public UUID submitInvoice(MultipartFile file, VendorReference vendorHint) {
        TenantId tenantId = TenantContext.getCurrentTenant();

        log.info("Submitting invoice for tenant: {}, filename: {}", tenantId, file.getOriginalFilename());

        try {
            // 1. Create invoice aggregate (RECEIVED status)
            UUID invoiceId = UUID.randomUUID();

            // 2. Upload document to S3
            DocumentReference documentReference = documentStorageService.uploadDocument(
                tenantId,
                invoiceId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize()
            );

            // 3. Create invoice aggregate
            Invoice invoice = Invoice.create(tenantId, documentReference, vendorHint);

            // 4. Persist invoice (publishes InvoiceReceivedEvent)
            invoice = invoiceRepository.save(invoice);

            log.info("Invoice created: invoiceId={}, documentKey={}", invoiceId, documentReference.getS3BucketKey());

            // 5. Initiate extraction asynchronously
            initiateExtraction(invoice);

            return invoice.getInvoiceId();

        } catch (IOException e) {
            log.error("Failed to read uploaded file: {}", file.getOriginalFilename(), e);
            throw new InvoiceIngestionException("Failed to read uploaded document", e);
        }
    }

    /**
     * Initiate AI extraction process.
     * Transitions invoice to EXTRACTING status and calls Python AI service.
     */
    @Transactional
    public void initiateExtraction(Invoice invoice) {
        log.info("Initiating extraction for invoice: {}", invoice.getInvoiceId());

        // Transition to EXTRACTING status
        invoice.startExtraction();
        invoiceRepository.save(invoice);

        // Generate presigned URL for Python service to download document
        String presignedUrl = documentStorageService.getPresignedUrl(
            invoice.getDocumentReference(),
            30 // 30 minutes expiration
        );

        // Call Python AI extraction service asynchronously
        extractionServiceClient.requestExtraction(
            invoice.getInvoiceId(),
            invoice.getTenantId(),
            presignedUrl,
            invoice.getDocumentReference().getDocumentFormat()
        );

        log.info("Extraction request sent to AI service: invoiceId={}", invoice.getInvoiceId());
    }

    /**
     * Handle extraction completion from AI service.
     * Called by callback/event handler when Python service completes extraction.
     *
     * @param invoiceId invoice identifier
     * @param extractionResult AI extraction result
     * @param metadata extracted invoice metadata
     * @param lineItems extracted line items
     */
    @Transactional
    public void handleExtractionComplete(
        UUID invoiceId,
        ExtractionResult extractionResult,
        InvoiceMetadata metadata,
        List<LineItem> lineItems
    ) {
        TenantId tenantId = TenantContext.getCurrentTenant();

        log.info("Handling extraction completion for invoice: {}", invoiceId);

        Invoice invoice = invoiceRepository.findById(invoiceId, tenantId)
            .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId));

        // Complete extraction (validates and publishes InvoiceExtractedEvent)
        invoice.completeExtraction(extractionResult, metadata, lineItems);

        // Persist (publishes domain events)
        invoiceRepository.save(invoice);

        log.info("Extraction completed successfully for invoice: {}, confidence: {}",
            invoiceId, extractionResult.getConfidenceScore());
    }

    /**
     * Handle extraction failure from AI service.
     *
     * @param invoiceId invoice identifier
     * @param extractionResult extraction result with errors
     * @param failureReason reason for failure
     */
    @Transactional
    public void handleExtractionFailed(
        UUID invoiceId,
        ExtractionResult extractionResult,
        String failureReason
    ) {
        TenantId tenantId = TenantContext.getCurrentTenant();

        log.warn("Handling extraction failure for invoice: {}, reason: {}", invoiceId, failureReason);

        Invoice invoice = invoiceRepository.findById(invoiceId, tenantId)
            .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId));

        // Fail extraction (may retry or permanently fail)
        invoice.failExtraction(extractionResult, failureReason);

        // Persist (may publish ExtractionFailedEvent if max retries exceeded)
        invoice = invoiceRepository.save(invoice);

        // Retry if not permanently failed
        if (invoice.getIngestionStatus() == Invoice.IngestionStatus.EXTRACTING) {
            log.info("Retrying extraction for invoice: {}, attempt: {}",
                invoiceId, extractionResult.getRetryCount() + 1);
            initiateExtraction(invoice);
        }
    }

    /**
     * Manually correct invoice metadata (human intervention).
     *
     * @param invoiceId invoice identifier
     * @param correctedMetadata corrected metadata
     * @param userId user making correction
     * @param reason reason for correction
     */
    @Transactional
    public void correctInvoiceMetadata(
        UUID invoiceId,
        InvoiceMetadata correctedMetadata,
        UUID userId,
        String reason
    ) {
        TenantId tenantId = TenantContext.getCurrentTenant();

        log.info("Correcting metadata for invoice: {} by user: {}", invoiceId, userId);

        Invoice invoice = invoiceRepository.findById(invoiceId, tenantId)
            .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId));

        invoice.correctMetadata(correctedMetadata, userId, reason);

        invoiceRepository.save(invoice);

        log.info("Metadata corrected for invoice: {}", invoiceId);
    }

    /**
     * Get invoice by ID.
     *
     * @param invoiceId invoice identifier
     * @return invoice aggregate
     */
    @Transactional(readOnly = true)
    public Invoice getInvoice(UUID invoiceId) {
        TenantId tenantId = TenantContext.getCurrentTenant();

        return invoiceRepository.findById(invoiceId, tenantId)
            .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId));
    }

    /**
     * Download invoice document.
     *
     * @param invoiceId invoice identifier
     * @return document input stream
     */
    @Transactional(readOnly = true)
    public java.io.InputStream downloadInvoiceDocument(UUID invoiceId) {
        Invoice invoice = getInvoice(invoiceId);
        return documentStorageService.downloadDocument(invoice.getDocumentReference());
    }

    /**
     * Custom exception for invoice ingestion errors.
     */
    public static class InvoiceIngestionException extends RuntimeException {
        public InvoiceIngestionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Custom exception for invoice not found.
     */
    public static class InvoiceNotFoundException extends RuntimeException {
        public InvoiceNotFoundException(String message) {
            super(message);
        }
    }
}
