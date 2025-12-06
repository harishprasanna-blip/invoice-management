package com.invoicemanagement.exceptionhandling.domain;

/**
 * Enumeration of resolution strategies for exception cases.
 *
 * DDD Pattern: Value Object (Enum)
 * Bounded Context: ExceptionHandlingContext
 */
public enum ResolutionStrategy {
    /**
     * Accept the variance/exception and proceed with payment.
     */
    ACCEPT_AND_PROCEED,

    /**
     * Request correction from vendor (new invoice).
     */
    REQUEST_VENDOR_CORRECTION,

    /**
     * Manually adjust invoice data in system.
     */
    MANUAL_ADJUSTMENT,

    /**
     * Create credit memo to offset variance.
     */
    CREDIT_MEMO,

    /**
     * Hold payment pending investigation.
     */
    HOLD_PENDING_INVESTIGATION,

    /**
     * Reject invoice entirely.
     */
    REJECT_INVOICE,

    /**
     * Override compliance check (requires approval).
     */
    COMPLIANCE_OVERRIDE,

    /**
     * Update PO or GR data in SAP.
     */
    UPDATE_PROCUREMENT_DATA,

    /**
     * Pay partial amount (variance amount withheld).
     */
    PARTIAL_PAYMENT,

    /**
     * Escalate to vendor management team.
     */
    ESCALATE_TO_VENDOR_MANAGEMENT
}
