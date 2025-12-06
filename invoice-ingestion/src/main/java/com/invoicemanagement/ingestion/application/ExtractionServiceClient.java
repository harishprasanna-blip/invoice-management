package com.invoicemanagement.ingestion.application;

import com.invoicemanagement.ingestion.domain.DocumentReference.DocumentFormat;
import com.invoicemanagement.sharedkernel.domain.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Client for calling Python AI document extraction service.
 * Handles async communication with FastAPI extraction service.
 *
 * Pattern: Anti-Corruption Layer / Adapter
 * Integration: Spring Boot (Java) → Python (FastAPI) via REST
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExtractionServiceClient {

    private final WebClient webClient;

    @Value("${python.extraction-service.base-url}")
    private String extractionServiceBaseUrl;

    /**
     * Request document extraction from Python AI service.
     *
     * @param invoiceId invoice identifier
     * @param tenantId tenant identifier
     * @param documentUrl presigned S3 URL for document
     * @param documentFormat document format type
     */
    public void requestExtraction(
        UUID invoiceId,
        TenantId tenantId,
        String documentUrl,
        DocumentFormat documentFormat
    ) {
        log.info("Requesting extraction from AI service: invoiceId={}, format={}", invoiceId, documentFormat);

        // Prepare request payload
        Map<String, Object> requestBody = Map.of(
            "correlationId", UUID.randomUUID().toString(),
            "tenantId", tenantId.asString(),
            "invoiceId", invoiceId.toString(),
            "documentReference", Map.of(
                "s3Url", documentUrl,
                "documentFormat", documentFormat.name()
            ),
            "extractionHints", Map.of()
        );

        // Call Python service asynchronously (fire-and-forget)
        webClient.post()
            .uri(extractionServiceBaseUrl + "/api/v1/extract")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(ExtractionResponse.class)
            .doOnSuccess(response ->
                log.info("Extraction request accepted: invoiceId={}, correlationId={}",
                    invoiceId, response.correlationId())
            )
            .doOnError(error ->
                log.error("Extraction request failed: invoiceId={}", invoiceId, error)
            )
            .onErrorResume(error -> Mono.empty()) // Don't fail main flow on AI service error
            .subscribe(); // Fire-and-forget (result comes back via event/callback)
    }

    /**
     * Response from extraction service.
     */
    public record ExtractionResponse(
        String correlationId,
        String status,
        Integer estimatedCompletionSeconds
    ) {}
}
