package com.invoicemanagement.validation.domain;

import com.invoicemanagement.sharedkernel.domain.Money;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain service for performing 2-way and 3-way matching.
 * Implements tolerance-based variance detection.
 *
 * DDD Pattern: Domain Service
 * Bounded Context: ValidationContext
 */
@Service
public class ThreeWayMatchingService {

    // Default tolerance thresholds (configurable per tenant in production)
    private static final BigDecimal DEFAULT_PRICE_TOLERANCE_PERCENT = new BigDecimal("2.0");
    private static final BigDecimal DEFAULT_QUANTITY_TOLERANCE_PERCENT = new BigDecimal("5.0");
    private static final BigDecimal DEFAULT_TOTAL_TOLERANCE_PERCENT = new BigDecimal("1.0");

    /**
     * Perform 2-way matching: Invoice vs Purchase Order only.
     *
     * @param invoiceReference Invoice to validate
     * @param purchaseOrderReference PO to match against
     * @return Matching result with variances
     */
    public MatchingResult performTwoWayMatch(
        PayableTransaction.InvoiceReference invoiceReference,
        PurchaseOrderReference purchaseOrderReference
    ) {
        return performTwoWayMatch(
            invoiceReference,
            purchaseOrderReference,
            DEFAULT_PRICE_TOLERANCE_PERCENT,
            DEFAULT_TOTAL_TOLERANCE_PERCENT
        );
    }

    /**
     * Perform 2-way matching with custom tolerances.
     */
    public MatchingResult performTwoWayMatch(
        PayableTransaction.InvoiceReference invoiceReference,
        PurchaseOrderReference purchaseOrderReference,
        BigDecimal priceTolerancePercent,
        BigDecimal totalTolerancePercent
    ) {
        List<MatchingResult.Variance> variances = new ArrayList<>();

        // Validate vendor
        // In production: This would validate vendorId from invoice metadata
        // For now, we just validate currency
        if (!invoiceReference.getCurrency().equals(purchaseOrderReference.getCurrency())) {
            variances.add(new MatchingResult.Variance(
                MatchingResult.VarianceType.TOTAL,
                purchaseOrderReference.getCurrency(),
                invoiceReference.getCurrency(),
                BigDecimal.valueOf(100),
                false,
                "Currency mismatch: expected " + purchaseOrderReference.getCurrency() +
                    ", actual " + invoiceReference.getCurrency()
            ));
            return MatchingResult.create(
                MatchingResult.MatchType.TWO_WAY,
                MatchingResult.MatchStatus.MISMATCH,
                List.of(),
                variances,
                BigDecimal.ZERO
            );
        }

        // Compare total amounts
        Money invoiceTotal = invoiceReference.getTotalAmount();
        Money poTotal = purchaseOrderReference.getPoAmount();

        BigDecimal totalDiffPercent = calculatePercentageDifference(
            poTotal.getAmount(),
            invoiceTotal.getAmount()
        );

        boolean totalWithinTolerance = totalDiffPercent.abs().compareTo(totalTolerancePercent) <= 0;

        if (totalDiffPercent.compareTo(BigDecimal.ZERO) != 0) {
            variances.add(MatchingResult.Variance.totalVariance(
                poTotal,
                invoiceTotal,
                totalDiffPercent,
                totalWithinTolerance
            ));
        }

        // Determine match status
        MatchingResult.MatchStatus matchStatus = determineMatchStatus(variances);

        // Calculate overall score (0-100)
        BigDecimal overallScore = calculateOverallScore(variances, totalTolerancePercent);

        return MatchingResult.create(
            MatchingResult.MatchType.TWO_WAY,
            matchStatus,
            List.of(), // No line-level matching for 2-way
            variances,
            overallScore
        );
    }

    /**
     * Perform 3-way matching: Invoice vs PO vs Goods Receipt.
     *
     * @param invoiceReference Invoice to validate
     * @param purchaseOrderReference PO to match against
     * @param goodsReceiptReference GR to match against
     * @return Matching result with variances
     */
    public MatchingResult performThreeWayMatch(
        PayableTransaction.InvoiceReference invoiceReference,
        PurchaseOrderReference purchaseOrderReference,
        GoodsReceiptReference goodsReceiptReference
    ) {
        return performThreeWayMatch(
            invoiceReference,
            purchaseOrderReference,
            goodsReceiptReference,
            DEFAULT_PRICE_TOLERANCE_PERCENT,
            DEFAULT_QUANTITY_TOLERANCE_PERCENT,
            DEFAULT_TOTAL_TOLERANCE_PERCENT
        );
    }

    /**
     * Perform 3-way matching with custom tolerances.
     */
    public MatchingResult performThreeWayMatch(
        PayableTransaction.InvoiceReference invoiceReference,
        PurchaseOrderReference purchaseOrderReference,
        GoodsReceiptReference goodsReceiptReference,
        BigDecimal priceTolerancePercent,
        BigDecimal quantityTolerancePercent,
        BigDecimal totalTolerancePercent
    ) {
        List<MatchingResult.Variance> variances = new ArrayList<>();
        List<MatchingResult.LineItemMatch> lineItemMatches = new ArrayList<>();

        // Validate currency
        if (!invoiceReference.getCurrency().equals(purchaseOrderReference.getCurrency())) {
            variances.add(new MatchingResult.Variance(
                MatchingResult.VarianceType.TOTAL,
                purchaseOrderReference.getCurrency(),
                invoiceReference.getCurrency(),
                BigDecimal.valueOf(100),
                false,
                "Currency mismatch"
            ));
            return MatchingResult.create(
                MatchingResult.MatchType.THREE_WAY,
                MatchingResult.MatchStatus.MISMATCH,
                lineItemMatches,
                variances,
                BigDecimal.ZERO
            );
        }

        // Validate GR is linked to same PO
        if (!goodsReceiptReference.getPoNumber().equals(purchaseOrderReference.getPoNumber())) {
            variances.add(new MatchingResult.Variance(
                MatchingResult.VarianceType.TOTAL,
                purchaseOrderReference.getPoNumber(),
                goodsReceiptReference.getPoNumber(),
                BigDecimal.valueOf(100),
                false,
                "Goods Receipt PO number mismatch"
            ));
            return MatchingResult.create(
                MatchingResult.MatchType.THREE_WAY,
                MatchingResult.MatchStatus.MISMATCH,
                lineItemMatches,
                variances,
                BigDecimal.ZERO
            );
        }

        // Compare total amounts (Invoice vs PO)
        Money invoiceTotal = invoiceReference.getTotalAmount();
        Money poTotal = purchaseOrderReference.getPoAmount();

        BigDecimal totalDiffPercent = calculatePercentageDifference(
            poTotal.getAmount(),
            invoiceTotal.getAmount()
        );

        boolean totalWithinTolerance = totalDiffPercent.abs().compareTo(totalTolerancePercent) <= 0;

        if (totalDiffPercent.compareTo(BigDecimal.ZERO) != 0) {
            variances.add(MatchingResult.Variance.totalVariance(
                poTotal,
                invoiceTotal,
                totalDiffPercent,
                totalWithinTolerance
            ));
        }

        // Line-level matching (Invoice line items vs PO line items vs GR line items)
        // In production: This would iterate through invoice line items
        // For simplicity, we create summary line match
        for (PurchaseOrderReference.POLineItem poLine : purchaseOrderReference.getPoLineItems()) {
            BigDecimal receivedQty = goodsReceiptReference.getReceivedQuantity(poLine.getLineNumber());

            BigDecimal qtyDiffPercent = calculatePercentageDifference(
                poLine.getOrderedQuantity(),
                receivedQty
            );

            boolean qtyWithinTolerance = qtyDiffPercent.abs().compareTo(quantityTolerancePercent) <= 0;

            if (qtyDiffPercent.compareTo(BigDecimal.ZERO) != 0 && !qtyWithinTolerance) {
                variances.add(MatchingResult.Variance.quantityVariance(
                    poLine.getOrderedQuantity(),
                    receivedQty,
                    qtyDiffPercent,
                    qtyWithinTolerance
                ));
            }

            lineItemMatches.add(new MatchingResult.LineItemMatch(
                poLine.getLineNumber(),
                poLine.getLineNumber(),
                poLine.getLineNumber(),
                qtyWithinTolerance,
                String.format("Quantity: PO=%s, GR=%s (%.2f%% diff)",
                    poLine.getOrderedQuantity(), receivedQty, qtyDiffPercent)
            ));
        }

        // Determine match status
        MatchingResult.MatchStatus matchStatus = determineMatchStatus(variances);

        // Calculate overall score
        BigDecimal overallScore = calculateOverallScore(variances, totalTolerancePercent);

        return MatchingResult.create(
            MatchingResult.MatchType.THREE_WAY,
            matchStatus,
            lineItemMatches,
            variances,
            overallScore
        );
    }

    /**
     * Calculate percentage difference between expected and actual values.
     *
     * @param expected Expected value
     * @param actual Actual value
     * @return Percentage difference (positive = actual > expected, negative = actual < expected)
     */
    private BigDecimal calculatePercentageDifference(BigDecimal expected, BigDecimal actual) {
        if (expected.compareTo(BigDecimal.ZERO) == 0) {
            return actual.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(100);
        }

        BigDecimal difference = actual.subtract(expected);
        return difference.divide(expected, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Determine match status based on variances.
     */
    private MatchingResult.MatchStatus determineMatchStatus(List<MatchingResult.Variance> variances) {
        if (variances.isEmpty()) {
            return MatchingResult.MatchStatus.MATCHED;
        }

        boolean hasVariancesExceedingTolerance = variances.stream()
            .anyMatch(v -> !v.isWithinTolerance());

        if (hasVariancesExceedingTolerance) {
            return MatchingResult.MatchStatus.MISMATCH;
        } else {
            return MatchingResult.MatchStatus.PARTIAL_MATCH;
        }
    }

    /**
     * Calculate overall matching score (0-100).
     * Higher score = better match.
     */
    private BigDecimal calculateOverallScore(
        List<MatchingResult.Variance> variances,
        BigDecimal maxTolerancePercent
    ) {
        if (variances.isEmpty()) {
            return BigDecimal.valueOf(100);
        }

        // Calculate average penalty from variances
        BigDecimal totalPenalty = variances.stream()
            .map(v -> {
                BigDecimal percentageDiff = v.getPercentageDiff().abs();
                // Normalize to tolerance (100% penalty at max tolerance)
                return percentageDiff.divide(maxTolerancePercent, 10, RoundingMode.HALF_UP)
                    .min(BigDecimal.valueOf(1.0));
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averagePenalty = totalPenalty.divide(
            BigDecimal.valueOf(variances.size()),
            10,
            RoundingMode.HALF_UP
        );

        // Score = 100 - (average penalty * 100)
        BigDecimal score = BigDecimal.valueOf(100).subtract(
            averagePenalty.multiply(BigDecimal.valueOf(100))
        );

        return score.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
