package com.invoicemanagement.sharedkernel.domain;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Value Object representing a monetary amount with currency.
 * Immutable and enforces business rules for monetary calculations.
 *
 * DDD Pattern: Value Object
 * Used across all bounded contexts for consistent monetary representation.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@ToString
public class Money implements Serializable, Comparable<Money> {

    private static final int DEFAULT_SCALE = 2;
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private String currencyCode;

    /**
     * Protected no-arg constructor for JPA.
     */
    protected Money() {
    }

    /**
     * Private constructor to enforce factory methods.
     */
    private Money(BigDecimal amount, Currency currency) {
        validateAmount(amount);
        Objects.requireNonNull(currency, "Currency cannot be null");

        this.amount = amount.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
        this.currencyCode = currency.getCurrencyCode();
    }

    /**
     * Factory method to create Money instance.
     */
    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    /**
     * Factory method with currency code.
     */
    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    /**
     * Factory method for zero amount.
     */
    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * Factory method for zero amount with currency code.
     */
    public static Money zero(String currencyCode) {
        return zero(Currency.getInstance(currencyCode));
    }

    /**
     * Get the Currency instance.
     */
    public Currency getCurrency() {
        return Currency.getInstance(currencyCode);
    }

    /**
     * Add another Money amount.
     * Invariant: Currencies must match.
     */
    public Money add(Money other) {
        ensureSameCurrency(other);
        return new Money(this.amount.add(other.amount), getCurrency());
    }

    /**
     * Subtract another Money amount.
     * Invariant: Currencies must match.
     */
    public Money subtract(Money other) {
        ensureSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), getCurrency());
    }

    /**
     * Multiply by a factor.
     */
    public Money multiply(BigDecimal factor) {
        validateAmount(factor);
        return new Money(this.amount.multiply(factor), getCurrency());
    }

    /**
     * Divide by a divisor.
     */
    public Money divide(BigDecimal divisor) {
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Cannot divide by zero");
        }
        return new Money(this.amount.divide(divisor, DEFAULT_SCALE, DEFAULT_ROUNDING), getCurrency());
    }

    /**
     * Calculate percentage of this amount.
     *
     * @param percentage percentage value (e.g., 10 for 10%)
     */
    public Money percentage(BigDecimal percentage) {
        BigDecimal percentageDecimal = percentage.divide(BigDecimal.valueOf(100), 10, DEFAULT_ROUNDING);
        return multiply(percentageDecimal);
    }

    /**
     * Calculate absolute value.
     */
    public Money abs() {
        return new Money(this.amount.abs(), getCurrency());
    }

    /**
     * Negate the amount.
     */
    public Money negate() {
        return new Money(this.amount.negate(), getCurrency());
    }

    /**
     * Check if amount is zero.
     */
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Check if amount is positive.
     */
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if amount is negative.
     */
    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Check if this amount is greater than another.
     */
    public boolean isGreaterThan(Money other) {
        ensureSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    /**
     * Check if this amount is less than another.
     */
    public boolean isLessThan(Money other) {
        ensureSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    /**
     * Check if this amount is greater than or equal to another.
     */
    public boolean isGreaterThanOrEqual(Money other) {
        ensureSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }

    /**
     * Check if this amount is less than or equal to another.
     */
    public boolean isLessThanOrEqual(Money other) {
        ensureSameCurrency(other);
        return this.amount.compareTo(other.amount) <= 0;
    }

    /**
     * Check if currencies match.
     */
    public boolean hasSameCurrency(Money other) {
        return this.currencyCode.equals(other.currencyCode);
    }

    @Override
    public int compareTo(Money other) {
        ensureSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    /**
     * Ensure currencies match for operations.
     */
    private void ensureSameCurrency(Money other) {
        if (!hasSameCurrency(other)) {
            throw new IllegalArgumentException(
                String.format("Currency mismatch: %s vs %s", this.currencyCode, other.currencyCode)
            );
        }
    }

    /**
     * Validate amount is not null.
     */
    private void validateAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
    }

    /**
     * Format for display (e.g., "USD 1,234.56").
     */
    public String toFormattedString() {
        return String.format("%s %,.2f", currencyCode, amount);
    }
}
