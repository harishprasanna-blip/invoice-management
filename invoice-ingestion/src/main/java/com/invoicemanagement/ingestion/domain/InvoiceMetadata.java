package com.invoicemanagement.ingestion.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicemanagement.sharedkernel.domain.Money;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/**
 * Value Object containing core invoice metadata.
 * Immutable invoice header information.
 *
 * DDD Pattern: Value Object
 * Bounded Context: InvoiceIngestionContext
 */
@Getter
@EqualsAndHashCode
@ToString
public class InvoiceMetadata implements Serializable {

    private final String invoiceNumber;
    private final LocalDate invoiceDate;
    private final LocalDate dueDate;
    private final Currency currency;
    private final Money totalAmount;

    @JsonCreator
    public InvoiceMetadata(
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("invoiceDate") LocalDate invoiceDate,
        @JsonProperty("dueDate") LocalDate dueDate,
        @JsonProperty("currency") Currency currency,
        @JsonProperty("totalAmount") Money totalAmount
    ) {
        this.invoiceNumber = Objects.requireNonNull(invoiceNumber, "Invoice number cannot be null");
        this.invoiceDate = Objects.requireNonNull(invoiceDate, "Invoice date cannot be null");
        this.dueDate = Objects.requireNonNull(dueDate, "Due date cannot be null");
        this.currency = Objects.requireNonNull(currency, "Currency cannot be null");
        this.totalAmount = Objects.requireNonNull(totalAmount, "Total amount cannot be null");

        validateInvariants();
    }

    /**
     * Enforce business invariants.
     */
    private void validateInvariants() {
        if (dueDate.isBefore(invoiceDate)) {
            throw new IllegalArgumentException(
                String.format("Due date (%s) cannot be before invoice date (%s)", dueDate, invoiceDate)
            );
        }

        if (!totalAmount.getCurrency().equals(currency)) {
            throw new IllegalArgumentException(
                String.format("Total amount currency (%s) must match invoice currency (%s)",
                    totalAmount.getCurrencyCode(), currency.getCurrencyCode())
            );
        }

        if (totalAmount.isNegative()) {
            throw new IllegalArgumentException("Total amount cannot be negative");
        }
    }

    /**
     * Check if invoice is overdue.
     */
    public boolean isOverdue() {
        return LocalDate.now().isAfter(dueDate);
    }

    /**
     * Get days until due (negative if overdue).
     */
    public long daysUntilDue() {
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
    }
}
