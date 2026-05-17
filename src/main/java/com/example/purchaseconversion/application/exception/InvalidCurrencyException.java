package com.example.purchaseconversion.application.exception;

import java.util.Objects;

/**
 * Thrown when {@code CurrencyAliasPort.resolve(input)} returns empty. Maps to HTTP 400
 * with {@code errorCode = INVALID_CURRENCY} (component-design.md §4; AC-021b/c).
 *
 * <p>The unresolved {@code currency} string is carried for the response body's
 * {@code details.currency} field. It is NOT logged in plain text — log emission must use
 * structured event {@code currency_alias.drift.detected.count{currency="<hashed>"}}
 * with the value passed through {@code DescriptionHasher} (Chunk B / D-12).
 */
public final class InvalidCurrencyException extends DomainException {

    private final String currency;

    public InvalidCurrencyException(String currency) {
        super("currency could not be resolved: " + Objects.requireNonNull(currency));
        this.currency = currency;
    }

    public String getCurrency() {
        return currency;
    }
}
