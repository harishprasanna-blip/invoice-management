package com.invoicemanagement.validation.domain;

import com.invoicemanagement.sharedkernel.domain.Money;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing the result of 2-way or 3-way matching.
 * Contains match status, variances, and overall score.
 *
 * DDD Pattern: Entity (within PayableTransaction aggregate)
 * Bounded Context: ValidationContext
 */
@Getter
@EqualsAndHashCode(of = "matchId")
@ToString
public class MatchingResult implements Serializable {

    private final UUID matchId;
    private final MatchType matchType;
    private final MatchStatus matchStatus;
    private final List<LineItemMatch> lineItemMatches;
    private final List<Variance> variances;
    private final BigDecimal overallScore; // 0-100
    private final Instant matchedAt;

    private MatchingResult(
        UUID matchId,
        MatchType matchType,
        MatchStatus matchStatus,
        List<LineItemMatch> lineItemMatches,
        List<Variance> variances,
        BigDecimal overallScore,
        Instant matchedAt
    ) {
        this.matchId = Objects.requireNonNull(matchId, "Match ID cannot be null");
        this.matchType = Objects.requireNonNull(matchType, "Match type cannot be null");
        this.matchStatus = Objects.requireNonNull(matchStatus, "Match status cannot be null");
        this.lineItemMatches = new ArrayList<>(lineItemMatches);
        this.variances = new ArrayList<>(variances);
        this.overallScore = overallScore;
        this.matchedAt = Objects.requireNonNull(matchedAt, "Matched timestamp cannot be null");
    }

    /**
     * Factory method for creating matching result.
     */
    public static MatchingResult create(
        MatchType matchType,
        MatchStatus matchStatus,
        List<LineItemMatch> lineItemMatches,
        List<Variance> variances,
        BigDecimal overallScore
    ) {
        return new MatchingResult(
            UUID.randomUUID(),
            matchType,
            matchStatus,
            lineItemMatches,
            variances,
            overallScore,
            Instant.now()
        );
    }

    /**
     * Check if matching passed (all variances within tolerance).
     */
    public boolean isPassed() {
        return matchStatus == MatchStatus.MATCHED;
    }

    /**
     * Check if has any variances.
     */
    public boolean hasVariances() {
        return !variances.isEmpty();
    }

    /**
     * Get variances exceeding tolerance.
     */
    public List<Variance> getVariancesExceedingTolerance() {
        return variances.stream()
            .filter(v -> !v.isWithinTolerance())
            .toList();
    }

    /**
     * Get immutable copy of line item matches.
     */
    public List<LineItemMatch> getLineItemMatches() {
        return Collections.unmodifiableList(lineItemMatches);
    }

    /**
     * Get immutable copy of variances.
     */
    public List<Variance> getVariances() {
        return Collections.unmodifiableList(variances);
    }

    /**
     * Match type enumeration.
     */
    public enum MatchType {
        TWO_WAY,    // Invoice vs PO only
        THREE_WAY,  // Invoice vs PO vs GR
        NO_PO       // Invoice only (no PO)
    }

    /**
     * Match status enumeration.
     */
    public enum MatchStatus {
        MATCHED,        // All checks passed
        PARTIAL_MATCH,  // Some variances within tolerance
        MISMATCH        // Variances exceed tolerance
    }

    /**
     * Line item match result.
     */
    @Getter
    @EqualsAndHashCode
    @ToString
    public static class LineItemMatch implements Serializable {
        private final String invoiceLineNumber;
        private final String poLineNumber;
        private final String grLineNumber;
        private final boolean matched;
        private final String matchDetails;

        public LineItemMatch(
            String invoiceLineNumber,
            String poLineNumber,
            String grLineNumber,
            boolean matched,
            String matchDetails
        ) {
            this.invoiceLineNumber = invoiceLineNumber;
            this.poLineNumber = poLineNumber;
            this.grLineNumber = grLineNumber;
            this.matched = matched;
            this.matchDetails = matchDetails;
        }
    }

    /**
     * Variance between expected and actual values.
     */
    @Getter
    @EqualsAndHashCode
    @ToString
    public static class Variance implements Serializable {
        private final VarianceType varianceType;
        private final Object expectedValue;
        private final Object actualValue;
        private final BigDecimal percentageDiff;
        private final boolean withinTolerance;
        private final String description;

        public Variance(
            VarianceType varianceType,
            Object expectedValue,
            Object actualValue,
            BigDecimal percentageDiff,
            boolean withinTolerance,
            String description
        ) {
            this.varianceType = Objects.requireNonNull(varianceType, "Variance type cannot be null");
            this.expectedValue = expectedValue;
            this.actualValue = actualValue;
            this.percentageDiff = percentageDiff;
            this.withinTolerance = withinTolerance;
            this.description = description;
        }

        public static Variance priceVariance(Money expected, Money actual, BigDecimal percentageDiff, boolean withinTolerance) {
            return new Variance(
                VarianceType.PRICE,
                expected,
                actual,
                percentageDiff,
                withinTolerance,
                String.format("Price variance: expected %s, actual %s (%.2f%%)",
                    expected.toFormattedString(), actual.toFormattedString(), percentageDiff)
            );
        }

        public static Variance quantityVariance(BigDecimal expected, BigDecimal actual, BigDecimal percentageDiff, boolean withinTolerance) {
            return new Variance(
                VarianceType.QUANTITY,
                expected,
                actual,
                percentageDiff,
                withinTolerance,
                String.format("Quantity variance: expected %s, actual %s (%.2f%%)", expected, actual, percentageDiff)
            );
        }

        public static Variance totalVariance(Money expected, Money actual, BigDecimal percentageDiff, boolean withinTolerance) {
            return new Variance(
                VarianceType.TOTAL,
                expected,
                actual,
                percentageDiff,
                withinTolerance,
                String.format("Total variance: expected %s, actual %s (%.2f%%)",
                    expected.toFormattedString(), actual.toFormattedString(), percentageDiff)
            );
        }
    }

    /**
     * Variance type enumeration.
     */
    public enum VarianceType {
        PRICE,      // Unit price variance
        QUANTITY,   // Quantity variance
        TAX,        // Tax amount variance
        TOTAL       // Total amount variance
    }
}
