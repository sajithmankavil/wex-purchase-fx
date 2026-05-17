package com.example.purchaseconversion.application.conversion;

import com.example.purchaseconversion.domain.ExchangeRate;
import com.example.purchaseconversion.domain.Money;
import com.example.purchaseconversion.domain.Purchase;

import java.util.Objects;

/**
 * Immutable application-layer record carrying the result of a successful conversion
 * (component-design.md §1, §3.2).
 *
 * <p>{@code purchase} — the source purchase (unchanged from storage).<br>
 * {@code rate} — the eligible {@link ExchangeRate} selected by
 * {@link com.example.purchaseconversion.domain.RateSelectionPolicy}.<br>
 * {@code convertedAmount} — {@code purchase.amountUsd()} multiplied by {@code rate.rate()},
 * rounded HALF_UP to scale 2 by {@link Money#multiply}.
 */
public record ConversionResult(
        Purchase purchase,
        ExchangeRate rate,
        Money convertedAmount) {

    public ConversionResult {
        Objects.requireNonNull(purchase, "purchase must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        Objects.requireNonNull(convertedAmount, "convertedAmount must not be null");
    }
}
