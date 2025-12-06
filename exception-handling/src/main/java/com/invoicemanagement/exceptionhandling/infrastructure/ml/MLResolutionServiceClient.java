package com.invoicemanagement.exceptionhandling.infrastructure.ml;

import com.invoicemanagement.exceptionhandling.domain.ExceptionCase;
import com.invoicemanagement.exceptionhandling.domain.MLRecommendation;
import com.invoicemanagement.exceptionhandling.domain.ResolutionStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client for Python ML Resolution Service.
 * Calls Azure OpenAI-powered recommendation engine.
 *
 * Infrastructure Layer - External Service Integration
 */
@Component
public class MLResolutionServiceClient {

    private final WebClient mlWebClient;

    public MLResolutionServiceClient(WebClient mlWebClient) {
        this.mlWebClient = mlWebClient;
    }

    /**
     * Get ML recommendation for exception case.
     *
     * @param exceptionCase Case to analyze
     * @return ML recommendation
     */
    public MLRecommendation getRecommendation(ExceptionCase exceptionCase) {
        try {
            MLRecommendationRequest request = buildRequest(exceptionCase);

            MLRecommendationResponse response = mlWebClient
                .post()
                .uri("/api/v1/recommend-resolution")
                .header("X-Tenant-ID", exceptionCase.getTenantId().getId().toString())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MLRecommendationResponse.class)
                .block();

            if (response == null) {
                return createFallbackRecommendation(exceptionCase);
            }

            return MLRecommendation.create(
                response.recommendedStrategy(),
                response.confidence(),
                response.reasoning(),
                response.similarCases(),
                response.automatable(),
                response.modelVersion()
            );

        } catch (Exception e) {
            // Log error and return fallback recommendation
            return createFallbackRecommendation(exceptionCase);
        }
    }

    /**
     * Find similar historical cases using vector similarity search.
     *
     * @param exceptionCase Case to find similarities for
     * @return List of similar case IDs
     */
    public List<String> findSimilarCases(ExceptionCase exceptionCase) {
        try {
            SimilarCasesRequest request = new SimilarCasesRequest(
                exceptionCase.getExceptionType().name(),
                exceptionCase.getExceptionDetails(),
                exceptionCase.getSeverity().name(),
                5 // Top 5 similar cases
            );

            SimilarCasesResponse response = mlWebClient
                .post()
                .uri("/api/v1/similar-cases")
                .header("X-Tenant-ID", exceptionCase.getTenantId().getId().toString())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SimilarCasesResponse.class)
                .block();

            return response != null ? response.similarCaseIds() : List.of();

        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Build ML service request from exception case.
     */
    private MLRecommendationRequest buildRequest(ExceptionCase exceptionCase) {
        return new MLRecommendationRequest(
            exceptionCase.getCaseId(),
            exceptionCase.getExceptionType().name(),
            exceptionCase.getSeverity().name(),
            exceptionCase.getExceptionDetails(),
            exceptionCase.getSourceContext(),
            exceptionCase.getSourceAggregateId()
        );
    }

    /**
     * Create fallback recommendation when ML service is unavailable.
     */
    private MLRecommendation createFallbackRecommendation(ExceptionCase exceptionCase) {
        // Rule-based fallback strategy
        ResolutionStrategy fallbackStrategy = switch (exceptionCase.getExceptionType()) {
            case MATCHING_MISMATCH -> ResolutionStrategy.HOLD_PENDING_INVESTIGATION;
            case COMPLIANCE_VIOLATION -> ResolutionStrategy.ESCALATE_TO_VENDOR_MANAGEMENT;
            case EXTRACTION_FAILED -> ResolutionStrategy.REQUEST_VENDOR_CORRECTION;
            case PAYMENT_FAILED -> ResolutionStrategy.HOLD_PENDING_INVESTIGATION;
            default -> ResolutionStrategy.ESCALATE_TO_VENDOR_MANAGEMENT;
        };

        return MLRecommendation.create(
            fallbackStrategy,
            new BigDecimal("0.50"), // Low confidence for fallback
            "ML service unavailable - using rule-based fallback",
            List.of(),
            false, // Not automatable
            "fallback-v1.0"
        );
    }

    // DTOs for ML service communication

    record MLRecommendationRequest(
        UUID caseId,
        String exceptionType,
        String severity,
        String exceptionDetails,
        String sourceContext,
        UUID sourceAggregateId
    ) {}

    record MLRecommendationResponse(
        ResolutionStrategy recommendedStrategy,
        BigDecimal confidence,
        String reasoning,
        List<String> similarCases,
        boolean automatable,
        String modelVersion
    ) {}

    record SimilarCasesRequest(
        String exceptionType,
        String exceptionDetails,
        String severity,
        int topK
    ) {}

    record SimilarCasesResponse(
        List<String> similarCaseIds,
        List<BigDecimal> similarityScores
    ) {}
}
