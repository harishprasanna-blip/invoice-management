package com.invoicemanagement.validation.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing a compliance validation check result.
 * Part of PayableTransaction aggregate.
 *
 * DDD Pattern: Entity (within PayableTransaction aggregate)
 * Bounded Context: ValidationContext
 */
@Getter
@EqualsAndHashCode(of = "checkId")
@ToString
public class ComplianceCheck implements Serializable {

    private final UUID checkId;
    private final ComplianceCheckType checkType;
    private final boolean passed;
    private final String violationDetails;
    private final Instant checkedAt;

    public ComplianceCheck(
        UUID checkId,
        ComplianceCheckType checkType,
        boolean passed,
        String violationDetails,
        Instant checkedAt
    ) {
        this.checkId = Objects.requireNonNull(checkId, "Check ID cannot be null");
        this.checkType = Objects.requireNonNull(checkType, "Check type cannot be null");
        this.passed = passed;
        this.violationDetails = violationDetails;
        this.checkedAt = Objects.requireNonNull(checkedAt, "Checked timestamp cannot be null");
    }

    /**
     * Factory method for passed compliance check.
     */
    public static ComplianceCheck passed(ComplianceCheckType checkType) {
        return new ComplianceCheck(
            UUID.randomUUID(),
            checkType,
            true,
            null,
            Instant.now()
        );
    }

    /**
     * Factory method for failed compliance check with violation details.
     */
    public static ComplianceCheck failed(
        ComplianceCheckType checkType,
        String violationDetails
    ) {
        return new ComplianceCheck(
            UUID.randomUUID(),
            checkType,
            false,
            Objects.requireNonNull(violationDetails, "Violation details required for failed check"),
            Instant.now()
        );
    }

    /**
     * Compliance check type enumeration.
     */
    public enum ComplianceCheckType {
        /**
         * GDPR data subject consent verification.
         * Ensures vendor has given consent for data processing.
         */
        GDPR_CONSENT,

        /**
         * GDPR data minimization check.
         * Ensures only necessary data is collected and stored.
         */
        GDPR_DATA_MINIMIZATION,

        /**
         * Tax validation check.
         * Validates tax rates, calculations, and compliance with local regulations.
         */
        TAX_VALIDATION,

        /**
         * Vendor sanctions screening.
         * Checks vendor against sanctions lists (OFAC, EU, etc.).
         */
        SANCTIONS_SCREENING,

        /**
         * Duplicate invoice detection.
         * Checks for duplicate invoice numbers from the same vendor.
         */
        DUPLICATE_CHECK,

        /**
         * Contract compliance check.
         * Validates invoice against contract terms (if applicable).
         */
        CONTRACT_COMPLIANCE
    }
}
