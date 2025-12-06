package com.invoicemanagement.validation.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicemanagement.sharedkernel.domain.Money;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Value Object representing Purchase Order reference from SAP.
 * Translated from SAP via Anti-Corruption Layer (ACL).
 *
 * DDD Pattern: Value Object
 * Bounded Context: ValidationContext
 * Source: SAP ERP (via ACL)
 */
@Getter
@EqualsAndHashCode
@ToString
public class PurchaseOrderReference implements Serializable {

    private final String poNumber;
    private final LocalDate poDate;
    private final String vendorId;
    private final String currency;
    private final Money poAmount;
    private final POStatus poStatus;
    private final List<POLineItem> poLineItems;

    @JsonCreator
    public PurchaseOrderReference(
        @JsonProperty("poNumber") String poNumber,
        @JsonProperty("poDate") LocalDate poDate,
        @JsonProperty("vendorId") String vendorId,
        @JsonProperty("currency") String currency,
        @JsonProperty("poAmount") Money poAmount,
        @JsonProperty("poStatus") POStatus poStatus,
        @JsonProperty("poLineItems") List<POLineItem> poLineItems
    ) {
        this.poNumber = Objects.requireNonNull(poNumber, "PO number cannot be null");
        this.poDate = Objects.requireNonNull(poDate, "PO date cannot be null");
        this.vendorId = Objects.requireNonNull(vendorId, "Vendor ID cannot be null");
        this.currency = Objects.requireNonNull(currency, "Currency cannot be null");
        this.poAmount = Objects.requireNonNull(poAmount, "PO amount cannot be null");
        this.poStatus = Objects.requireNonNull(poStatus, "PO status cannot be null");
        this.poLineItems = poLineItems != null ? List.copyOf(poLineItems) : Collections.emptyList();
    }

    public static PurchaseOrderReference of(
        String poNumber,
        LocalDate poDate,
        String vendorId,
        Money poAmount,
        List<POLineItem> lineItems
    ) {
        return new PurchaseOrderReference(
            poNumber,
            poDate,
            vendorId,
            poAmount.getCurrencyCode(),
            poAmount,
            POStatus.OPEN,
            lineItems
        );
    }

    /**
     * Check if PO is open for matching.
     */
    public boolean isOpenForMatching() {
        return poStatus == POStatus.OPEN;
    }

    /**
     * Get immutable copy of line items.
     */
    public List<POLineItem> getPoLineItems() {
        return Collections.unmodifiableList(poLineItems);
    }

    /**
     * PO line item value object.
     */
    @Getter
    @EqualsAndHashCode
    @ToString
    public static class POLineItem implements Serializable {
        private final String lineNumber;
        private final String materialId;
        private final String description;
        private final java.math.BigDecimal orderedQuantity;
        private final Money unitPrice;
        private final Money lineAmount;

        @JsonCreator
        public POLineItem(
            @JsonProperty("lineNumber") String lineNumber,
            @JsonProperty("materialId") String materialId,
            @JsonProperty("description") String description,
            @JsonProperty("orderedQuantity") java.math.BigDecimal orderedQuantity,
            @JsonProperty("unitPrice") Money unitPrice,
            @JsonProperty("lineAmount") Money lineAmount
        ) {
            this.lineNumber = Objects.requireNonNull(lineNumber, "Line number cannot be null");
            this.materialId = materialId;
            this.description = description;
            this.orderedQuantity = Objects.requireNonNull(orderedQuantity, "Ordered quantity cannot be null");
            this.unitPrice = Objects.requireNonNull(unitPrice, "Unit price cannot be null");
            this.lineAmount = Objects.requireNonNull(lineAmount, "Line amount cannot be null");
        }
    }

    /**
     * PO status enumeration.
     */
    public enum POStatus {
        OPEN,           // Open for matching
        PARTIALLY_BILLED, // Some lines billed
        FULLY_BILLED,   // All lines billed
        CLOSED,         // Closed (no more matching)
        CANCELLED       // Cancelled
    }
}
