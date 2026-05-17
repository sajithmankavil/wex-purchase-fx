package com.example.purchaseconversion.domain;

import java.util.Objects;

/**
 * Canonical Treasury currency descriptor (e.g., {@code "Canada-Dollar"}, {@code "Euro Zone-Euro"}).
 *
 * <p>The descriptor is stored verbatim in the form Treasury publishes it. Case-insensitive
 * matching and ISO-4217 alias resolution belong to the application/alias-table layer, not here.
 *
 * <p>Domain invariants:
 * <ul>
 *   <li>Non-null, non-blank.</li>
 *   <li>Length ≤ 64 (matches the {@code VARCHAR(64)} column in {@code exchange_rates}).</li>
 * </ul>
 *
 * <p>Note: the Phase-3 prototype found that the Eurozone canonical descriptor is
 * {@code "Euro Zone-Euro"} — with a space — not {@code "Euro-Zone-Euro"}. The descriptor
 * accepts and preserves the space.
 */
public record CurrencyDescriptor(String value) {

    /** Maximum permitted length; matches {@code exchange_rates.country_currency_desc VARCHAR(64)}. */
    public static final int MAX_LENGTH = 64;

    public CurrencyDescriptor {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "value length must be <= " + MAX_LENGTH + "; got " + value.length());
        }
    }

    public static CurrencyDescriptor of(String value) {
        return new CurrencyDescriptor(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
