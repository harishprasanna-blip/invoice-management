package com.invoicemanagement.exceptionhandling.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing ML-driven resolution recommendation.
 * Part of ExceptionCase aggregate.
 *
 * DDD Pattern: Entity (within ExceptionCase aggregate)
 * Bounded Context: ExceptionHandlingContext
 */
@Getter
@EqualsAndHashCode(of = "recommendationId")
@ToString
public class MLRecommendation implements Serializable {

    private final UUID recommendationId;
    private final ResolutionStrategy recommendedStrategy;
    private final BigDecimal confidence; // 0.0 to 1.0
    private final String reasoning;
    private final List<String> similarCases; // Case IDs of similar past cases
    private final boolean automatable; // Can this be auto-resolved?
    private final String mlModelVersion;
    private final Instant generatedAt;

    public MLRecommendation(
        UUID recommendationId,
        ResolutionStrategy recommendedStrategy,
        BigDecimal confidence,
        String reasoning,
        List<String> similarCases,
        boolean automatable,
        String mlModelVersion,
        Instant generatedAt
    ) {
        this.recommendationId = Objects.requireNonNull(recommendationId, "Recommendation ID cannot be null");
        this.recommendedStrategy = Objects.requireNonNull(recommendedStrategy, "Recommended strategy cannot be null");
        this.confidence = Objects.requireNonNull(confidence, "Confidence cannot be null");
        this.reasoning = reasoning;
        this.similarCases = similarCases != null ? List.copyOf(similarCases) : List.of();
        this.automatable = automatable;
        this.mlModelVersion = mlModelVersion;
        this.generatedAt = Objects.requireNonNull(generatedAt, "Generated timestamp cannot be null");

        // Validate confidence range
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
    }

    /**
     * Factory method for creating ML recommendation.
     */
    public static MLRecommendation create(
        ResolutionStrategy recommendedStrategy,
        BigDecimal confidence,
        String reasoning,
        List<String> similarCases,
        boolean automatable,
        String mlModelVersion
    ) {
        return new MLRecommendation(
            UUID.randomUUID(),
            recommendedStrategy,
            confidence,
            reasoning,
            similarCases,
            automatable,
            mlModelVersion,
            Instant.now()
        );
    }

    /**
     * Check if recommendation confidence is high enough for auto-resolution.
     */
    public boolean isHighConfidence() {
        return confidence.compareTo(new BigDecimal("0.85")) >= 0;
    }

    /**
     * Check if recommendation is suitable for automatic execution.
     */
    public boolean canAutoExecute() {
        return automatable && isHighConfidence();
    }
}
