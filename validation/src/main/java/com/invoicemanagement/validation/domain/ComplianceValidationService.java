package com.invoicemanagement.validation.domain;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Domain service for performing compliance validation checks.
 * Validates GDPR, tax, sanctions, and contract compliance.
 *
 * DDD Pattern: Domain Service
 * Bounded Context: ValidationContext
 */
@Service
public class ComplianceValidationService {

    // EU countries requiring GDPR compliance
    private static final Set<String> EU_COUNTRIES = Set.of(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
        "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
        "PL", "PT", "RO", "SK", "SI", "ES", "SE"
    );

    /**
     * Perform all compliance checks on payable transaction.
     *
     * @param transaction Transaction to validate
     * @param tenantCountryCode Tenant's country code (for region-specific checks)
     * @return List of compliance check results
     */
    public List<ComplianceCheck> performComplianceChecks(
        PayableTransaction transaction,
        String tenantCountryCode
    ) {
        List<ComplianceCheck> checks = new ArrayList<>();

        // GDPR checks (if EU tenant or EU vendor)
        if (isEUCountry(tenantCountryCode)) {
            checks.addAll(performGDPRChecks(transaction));
        }

        // Tax validation
        checks.add(performTaxValidation(transaction));

        // Sanctions screening
        checks.add(performSanctionsScreening(transaction));

        // Duplicate detection
        checks.add(performDuplicateCheck(transaction));

        return checks;
    }

    /**
     * Perform GDPR compliance checks.
     */
    private List<ComplianceCheck> performGDPRChecks(PayableTransaction transaction) {
        List<ComplianceCheck> checks = new ArrayList<>();

        // GDPR Consent Check
        // In production: Check if vendor has given data processing consent
        // For now, simplified check
        boolean hasConsent = checkVendorConsent(transaction);
        if (hasConsent) {
            checks.add(ComplianceCheck.passed(ComplianceCheck.ComplianceCheckType.GDPR_CONSENT));
        } else {
            checks.add(ComplianceCheck.failed(
                ComplianceCheck.ComplianceCheckType.GDPR_CONSENT,
                "Vendor has not provided GDPR data processing consent"
            ));
        }

        // GDPR Data Minimization Check
        // In production: Validate that only necessary PII is collected
        boolean meetsDataMinimization = checkDataMinimization(transaction);
        if (meetsDataMinimization) {
            checks.add(ComplianceCheck.passed(ComplianceCheck.ComplianceCheckType.GDPR_DATA_MINIMIZATION));
        } else {
            checks.add(ComplianceCheck.failed(
                ComplianceCheck.ComplianceCheckType.GDPR_DATA_MINIMIZATION,
                "Invoice contains excessive personal data beyond business necessity"
            ));
        }

        return checks;
    }

    /**
     * Perform tax validation check.
     */
    private ComplianceCheck performTaxValidation(PayableTransaction transaction) {
        // In production: Validate tax rates against tax authority databases
        // Check if tax amount matches expected calculation
        // Validate tax ID format

        boolean taxValid = validateTaxCalculation(transaction);

        if (taxValid) {
            return ComplianceCheck.passed(ComplianceCheck.ComplianceCheckType.TAX_VALIDATION);
        } else {
            return ComplianceCheck.failed(
                ComplianceCheck.ComplianceCheckType.TAX_VALIDATION,
                "Tax calculation does not match expected rates or formats"
            );
        }
    }

    /**
     * Perform sanctions screening check.
     */
    private ComplianceCheck performSanctionsScreening(PayableTransaction transaction) {
        // In production: Check vendor against OFAC, EU, UN sanctions lists
        // Use external sanctions screening API

        boolean isSanctioned = checkSanctionsList(transaction);

        if (!isSanctioned) {
            return ComplianceCheck.passed(ComplianceCheck.ComplianceCheckType.SANCTIONS_SCREENING);
        } else {
            return ComplianceCheck.failed(
                ComplianceCheck.ComplianceCheckType.SANCTIONS_SCREENING,
                "Vendor appears on sanctions list - payment blocked"
            );
        }
    }

    /**
     * Perform duplicate invoice detection.
     */
    private ComplianceCheck performDuplicateCheck(PayableTransaction transaction) {
        // In production: Query database for same invoice number from same vendor
        // Check if already paid or in process

        boolean isDuplicate = checkForDuplicate(transaction);

        if (!isDuplicate) {
            return ComplianceCheck.passed(ComplianceCheck.ComplianceCheckType.DUPLICATE_CHECK);
        } else {
            return ComplianceCheck.failed(
                ComplianceCheck.ComplianceCheckType.DUPLICATE_CHECK,
                "Duplicate invoice detected - same invoice number already processed"
            );
        }
    }

    // Helper methods (simplified - in production these would be more complex)

    private boolean isEUCountry(String countryCode) {
        return countryCode != null && EU_COUNTRIES.contains(countryCode.toUpperCase());
    }

    private boolean checkVendorConsent(PayableTransaction transaction) {
        // In production: Query consent database
        // Check if vendor has active GDPR consent on file
        // For now, assume consent exists
        return true;
    }

    private boolean checkDataMinimization(PayableTransaction transaction) {
        // In production: Analyze invoice fields for unnecessary PII
        // Check against data minimization policy
        // For now, pass check
        return true;
    }

    private boolean validateTaxCalculation(PayableTransaction transaction) {
        // In production: Fetch tax rates from tax authority API
        // Recalculate expected tax and compare with invoice
        // Validate tax ID format (VAT number, EIN, etc.)

        // Simplified validation: Check if PO exists and has tax info
        return transaction.getPurchaseOrderReference() != null;
    }

    private boolean checkSanctionsList(PayableTransaction transaction) {
        // In production: Call sanctions screening API (Dow Jones, Refinitiv, etc.)
        // Check vendor name, ID, and address against:
        // - OFAC SDN List (US)
        // - EU Sanctions List
        // - UN Security Council Consolidated List

        // For now, assume no sanctions
        return false;
    }

    private boolean checkForDuplicate(PayableTransaction transaction) {
        // In production: Query payable_transactions table
        // WHERE invoice_number = ? AND tenant_id = ? AND status != 'FAILED'
        // Check creation date to avoid false positives from resubmissions

        // For now, assume no duplicate
        return false;
    }

    /**
     * Validate specific compliance check type.
     * Used for selective re-validation after exception resolution.
     */
    public ComplianceCheck validateSpecificCheck(
        PayableTransaction transaction,
        ComplianceCheck.ComplianceCheckType checkType,
        String tenantCountryCode
    ) {
        return switch (checkType) {
            case GDPR_CONSENT, GDPR_DATA_MINIMIZATION -> {
                List<ComplianceCheck> gdprChecks = performGDPRChecks(transaction);
                yield gdprChecks.stream()
                    .filter(check -> check.getCheckType() == checkType)
                    .findFirst()
                    .orElse(ComplianceCheck.passed(checkType));
            }
            case TAX_VALIDATION -> performTaxValidation(transaction);
            case SANCTIONS_SCREENING -> performSanctionsScreening(transaction);
            case DUPLICATE_CHECK -> performDuplicateCheck(transaction);
            case CONTRACT_COMPLIANCE -> performContractComplianceCheck(transaction);
        };
    }

    /**
     * Perform contract compliance check.
     */
    private ComplianceCheck performContractComplianceCheck(PayableTransaction transaction) {
        // In production: If PO references a contract, validate:
        // - Invoice amount within contract limits
        // - Payment terms match contract
        // - Vendor is authorized under contract

        PurchaseOrderReference po = transaction.getPurchaseOrderReference();
        if (po == null) {
            // No PO, no contract to validate
            return ComplianceCheck.passed(ComplianceCheck.ComplianceCheckType.CONTRACT_COMPLIANCE);
        }

        // Simplified check: Validate invoice amount doesn't exceed PO amount
        BigDecimal invoiceAmount = transaction.getInvoiceReference().getTotalAmount().getAmount();
        BigDecimal poAmount = po.getPoAmount().getAmount();

        if (invoiceAmount.compareTo(poAmount.multiply(BigDecimal.valueOf(1.1))) > 0) {
            // Invoice exceeds PO by more than 10%
            return ComplianceCheck.failed(
                ComplianceCheck.ComplianceCheckType.CONTRACT_COMPLIANCE,
                String.format("Invoice amount (%s) exceeds PO amount (%s) by more than 10%%",
                    invoiceAmount, poAmount)
            );
        }

        return ComplianceCheck.passed(ComplianceCheck.ComplianceCheckType.CONTRACT_COMPLIANCE);
    }
}
