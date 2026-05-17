package com.example.purchaseconversion.application.exception;

import com.example.purchaseconversion.domain.CurrencyDescriptor;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Thrown when no eligible rate exists in the 6-month window even after a fresh
 * Treasury fetch + upsert. Maps to HTTP 422 with
 * {@code errorCode = CONVERSION_RATE_NOT_AVAILABLE} (component-design.md §4;
 * AC-020 / AC-020b / AC-022b).
 *
 * <p>Carries the purchase date, target currency, and window bounds for the response
 * body's {@code details} object.
 */
public final class ConversionRateNotAvailableException extends DomainException {

    private final LocalDate purchaseDate;
    private final CurrencyDescriptor targetCurrency;
    private final LocalDate windowLower;
    private final LocalDate windowUpper;

    public ConversionRateNotAvailableException(
            LocalDate purchaseDate,
            CurrencyDescriptor targetCurrency,
            LocalDate windowLower,
            LocalDate windowUpper) {
        super("no eligible rate in 6-month window for currency=" + Objects.requireNonNull(targetCurrency)
                + " purchaseDate=" + Objects.requireNonNull(purchaseDate)
                + " window=[" + Objects.requireNonNull(windowLower)
                + ", " + Objects.requireNonNull(windowUpper) + "]");
        this.purchaseDate = purchaseDate;
        this.targetCurrency = targetCurrency;
        this.windowLower = windowLower;
        this.windowUpper = windowUpper;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public CurrencyDescriptor getTargetCurrency() {
        return targetCurrency;
    }

    public LocalDate getWindowLower() {
        return windowLower;
    }

    public LocalDate getWindowUpper() {
        return windowUpper;
    }
}
