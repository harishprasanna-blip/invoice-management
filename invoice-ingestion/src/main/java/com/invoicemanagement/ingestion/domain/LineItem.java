package com.invoicemanagement.ingestion.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicemanagement.sharedkernel.domain.Money;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value Object representing an individual line item on an invoice.
 * Immutable product/service entry with pricing and tax.
 *
 * DDD Pattern: Value Object
 * Bounded Context: InvoiceIngestionContext
 */
@Getter
@EqualsAndHashCode
@ToString
public class LineItem implements Serializable {

    private final int lineNumber;
    private final String description;
    private final Quantity quantity;
    private final Money unitPrice;
    private final Money taxAmount;
    private final Money totalAmount;
    private final String glAccount; // Optional general ledger account code

    @JsonCreator
    public LineItem(
        @JsonProperty("lineNumber") int lineNumber,
        @JsonProperty("description") String description,
        @JsonProperty("quantity") Quantity quantity,
        @JsonProperty("unitPrice") Money unitPrice,
        @JsonProperty("taxAmount") Money taxAmount,
        @JsonProperty("totalAmount") Money totalAmount,
        @JsonProperty("glAccount") String glAccount
    ) {
        this.lineNumber = lineNumber;
        this.description = Objects.requireNonNull(description, "Description cannot be null");
        this.quantity = Objects.requireNonNull(quantity, "Quantity cannot be null");
        this.unitPrice = Objects.requireNonNull(unitPrice, "Unit price cannot be null");
        this.taxAmount = taxAmount != null ? taxAmount : Money.zero(unitPrice.getCurrency());
        this.totalAmount = Objects.requireNonNull(totalAmount, "Total amount cannot be null");
        this.glAccount = glAccount;

        validateInvariants();
    }

    /**
     * Enforce business invariants.
     */
    private void validateInvariants() {
        if (lineNumber <= 0) {
            throw new IllegalArgumentException("Line number must be positive");
        }

        if (quantity.isZeroOrNegative()) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (unitPrice.isNegative()) {
            throw new IllegalArgumentException("Unit price cannot be negative");
        }

        // Validate total = (quantity * unitPrice) + tax (with tolerance)
        Money calculatedSubtotal = unitPrice.multiply(quantity.getValue());
        Money calculatedTotal = calculatedSubtotal.add(taxAmount);

        // Allow 0.01 tolerance for rounding
        Money tolerance = Money.of(BigDecimal.valueOf(0.01), totalAmount.getCurrency());
        Money difference = calculatedTotal.subtract(totalAmount).abs();

        if (difference.isGreaterThan(tolerance)) {
            throw new IllegalArgumentException(
                String.format("Line total (%s) does not match calculated total (%s)",
                    totalAmount, calculatedTotal)
            );
        }
    }

    /**
     * Calculate net amount (without tax).
     */
    public Money getNetAmount() {
        return totalAmount.subtract(taxAmount);
    }

    /**
     * Check if GL account is assigned.
     */
    public boolean hasGlAccount() {
        return glAccount != null && !glAccount.isBlank();
    }

    /**
     * Value Object for quantity with unit of measure.
     */
    @Getter
    @EqualsAndHashCode
    @ToString
    public static class Quantity implements Serializable {
        private final BigDecimal value;
        private final Unit unit;

        @JsonCreator
        public Quantity(
            @JsonProperty("value") BigDecimal value,
            @JsonProperty("unit") Unit unit
        ) {
            this.value = Objects.requireNonNull(value, "Quantity value cannot be null");
            this.unit = Objects.requireNonNull(unit, "Unit cannot be null");
        }

        public static Quantity of(BigDecimal value, Unit unit) {
            return new Quantity(value, unit);
        }

        public static Quantity each(BigDecimal value) {
            return new Quantity(value, Unit.EACH);
        }

        public boolean isZeroOrNegative() {
            return value.compareTo(BigDecimal.ZERO) <= 0;
        }

        public enum Unit {
            EACH,    // Individual items
            HOUR,    // Time-based services
            KG,      // Kilograms
            LB,      // Pounds
            METER,   // Meters
            LITER,   // Liters
            BOX,     // Boxed items
            DOZEN    // Dozens
        }
    }
}
