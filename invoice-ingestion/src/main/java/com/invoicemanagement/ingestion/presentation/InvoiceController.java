package com.invoicemanagement.ingestion.presentation;

import com.invoicemanagement.ingestion.application.InvoiceIngestionService;
import com.invoicemanagement.ingestion.domain.Invoice;
import com.invoicemanagement.ingestion.domain.InvoiceMetadata;
import com.invoicemanagement.ingestion.domain.VendorReference;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * REST API controller for invoice ingestion.
 * Exposes HTTP endpoints for invoice submission and retrieval.
 *
 * Pattern: REST Controller (Presentation Layer)
 * API Version: v1
 * Base Path: /api/v1/invoices
 */
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@Slf4j
public class InvoiceController {

    private final InvoiceIngestionService invoiceIngestionService;

    /**
     * Submit invoice document for processing.
     *
     * POST /api/v1/invoices
     *
     * @param file invoice document file (PDF, EDI, XML, etc.)
     * @param vendorId optional vendor ID hint
     * @param vendorName optional vendor name hint
     * @return invoice submission response with invoice ID
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InvoiceSubmissionResponse> submitInvoice(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "vendorId", required = false) String vendorId,
        @RequestParam(value = "vendorName", required = false) String vendorName
    ) {
        log.info("Received invoice submission request: filename={}, size={} bytes",
            file.getOriginalFilename(), file.getSize());

        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                new InvoiceSubmissionResponse(null, "FAILED", "File is empty", null)
            );
        }

        // Validate file size (max 50MB)
        if (file.getSize() > 50 * 1024 * 1024) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                new InvoiceSubmissionResponse(null, "FAILED", "File size exceeds 50MB limit", null)
            );
        }

        try {
            // Create vendor hint if provided
            VendorReference vendorHint = null;
            if (vendorId != null && vendorName != null) {
                vendorHint = VendorReference.of(vendorId, vendorName);
            }

            // Submit invoice
            UUID invoiceId = invoiceIngestionService.submitInvoice(file, vendorHint);

            InvoiceSubmissionResponse response = new InvoiceSubmissionResponse(
                invoiceId.toString(),
                "RECEIVED",
                "Invoice submitted successfully for extraction",
                "Estimated processing time: 15-30 seconds"
            );

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (Exception e) {
            log.error("Failed to submit invoice", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new InvoiceSubmissionResponse(null, "FAILED", "Failed to submit invoice: " + e.getMessage(), null)
            );
        }
    }

    /**
     * Get invoice status and details.
     *
     * GET /api/v1/invoices/{invoiceId}
     *
     * @param invoiceId invoice identifier
     * @return invoice details
     */
    @GetMapping("/{invoiceId}")
    public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable UUID invoiceId) {
        log.info("Fetching invoice: invoiceId={}", invoiceId);

        try {
            Invoice invoice = invoiceIngestionService.getInvoice(invoiceId);

            InvoiceResponse response = InvoiceResponse.fromDomain(invoice);

            return ResponseEntity.ok(response);

        } catch (InvoiceIngestionService.InvoiceNotFoundException e) {
            log.warn("Invoice not found: {}", invoiceId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to fetch invoice: {}", invoiceId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Download original invoice document.
     *
     * GET /api/v1/invoices/{invoiceId}/document
     *
     * @param invoiceId invoice identifier
     * @return document file
     */
    @GetMapping("/{invoiceId}/document")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable UUID invoiceId) {
        log.info("Downloading document for invoice: {}", invoiceId);

        try {
            Invoice invoice = invoiceIngestionService.getInvoice(invoiceId);
            InputStream documentStream = invoiceIngestionService.downloadInvoiceDocument(invoiceId);

            InputStreamResource resource = new InputStreamResource(documentStream);

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition",
                    "attachment; filename=\"" + invoice.getDocumentReference().getOriginalFileName() + "\"")
                .body(resource);

        } catch (InvoiceIngestionService.InvoiceNotFoundException e) {
            log.warn("Invoice not found: {}", invoiceId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to download document: {}", invoiceId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Manually correct invoice metadata.
     *
     * PUT /api/v1/invoices/{invoiceId}/metadata
     *
     * @param invoiceId invoice identifier
     * @param request correction request
     * @return updated invoice
     */
    @PutMapping("/{invoiceId}/metadata")
    public ResponseEntity<Void> correctMetadata(
        @PathVariable UUID invoiceId,
        @Valid @RequestBody MetadataCorrectionRequest request
    ) {
        log.info("Correcting metadata for invoice: {}", invoiceId);

        try {
            // In real implementation, get userId from security context
            UUID userId = UUID.randomUUID(); // Placeholder

            invoiceIngestionService.correctInvoiceMetadata(
                invoiceId,
                request.toDomain(),
                userId,
                request.reason()
            );

            return ResponseEntity.noContent().build();

        } catch (InvoiceIngestionService.InvoiceNotFoundException e) {
            log.warn("Invoice not found: {}", invoiceId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to correct metadata: {}", invoiceId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Response for invoice submission.
     */
    public record InvoiceSubmissionResponse(
        String invoiceId,
        String status,
        String message,
        String estimatedProcessingTime
    ) {}

    /**
     * Response for invoice details.
     */
    public record InvoiceResponse(
        String invoiceId,
        String tenantId,
        String ingestionStatus,
        VendorInfo vendor,
        InvoiceInfo invoiceInfo,
        ExtractionInfo extractionInfo,
        DocumentInfo documentInfo
    ) {
        public static InvoiceResponse fromDomain(Invoice invoice) {
            VendorInfo vendorInfo = invoice.getVendorReference() != null
                ? new VendorInfo(
                    invoice.getVendorReference().getVendorId(),
                    invoice.getVendorReference().getVendorName(),
                    invoice.getVendorReference().getTaxId()
                )
                : null;

            InvoiceInfo invoiceInfo = invoice.getInvoiceMetadata() != null
                ? new InvoiceInfo(
                    invoice.getInvoiceMetadata().getInvoiceNumber(),
                    invoice.getInvoiceMetadata().getInvoiceDate().toString(),
                    invoice.getInvoiceMetadata().getDueDate().toString(),
                    invoice.getInvoiceMetadata().getCurrency().getCurrencyCode(),
                    invoice.getInvoiceMetadata().getTotalAmount().toFormattedString(),
                    invoice.getLineItems().size()
                )
                : null;

            ExtractionInfo extractionInfo = invoice.getExtractionResult() != null
                ? new ExtractionInfo(
                    invoice.getExtractionResult().getExtractionId().toString(),
                    invoice.getExtractionResult().getConfidenceScore().toString(),
                    invoice.getExtractionResult().getExtractionMethod().name(),
                    invoice.getExtractionResult().getRetryCount()
                )
                : null;

            DocumentInfo documentInfo = new DocumentInfo(
                invoice.getDocumentReference().getOriginalFileName(),
                invoice.getDocumentReference().getDocumentFormat().name(),
                invoice.getDocumentReference().getUploadTimestamp().toString()
            );

            return new InvoiceResponse(
                invoice.getInvoiceId().toString(),
                invoice.getTenantId().asString(),
                invoice.getIngestionStatus().name(),
                vendorInfo,
                invoiceInfo,
                extractionInfo,
                documentInfo
            );
        }

        public record VendorInfo(String vendorId, String vendorName, String taxId) {}
        public record InvoiceInfo(String invoiceNumber, String invoiceDate, String dueDate,
                                  String currency, String totalAmount, int lineItemCount) {}
        public record ExtractionInfo(String extractionId, String confidenceScore,
                                      String extractionMethod, int retryCount) {}
        public record DocumentInfo(String originalFileName, String documentFormat, String uploadTimestamp) {}
    }

    /**
     * Request for metadata correction.
     */
    public record MetadataCorrectionRequest(
        String invoiceNumber,
        String invoiceDate,
        String dueDate,
        String currency,
        String totalAmount,
        String reason
    ) {
        public InvoiceMetadata toDomain() {
            // Convert request to domain InvoiceMetadata
            // In real implementation, add proper parsing and validation
            return null; // Placeholder
        }
    }
}
