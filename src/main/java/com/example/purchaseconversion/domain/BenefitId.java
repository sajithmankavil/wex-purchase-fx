package com.example.purchaseconversion.domain;

import java.util.Objects;

/**
 * Opaque benefit-catalog identifier (eligibility-endpoint-spec.md §5 scope-affecting #5 —
 * the real catalog's ID scheme isn't specified upstream; this assumes a short opaque
 * string, e.g. {@code "BEN-1042"}).
 *
 * <p>Domain invariants: non-null, non-blank, length ≤ 64 (matches the bound this
 * codebase uses elsewhere for similar identifier-shaped columns, e.g. {@link CurrencyDescriptor}).
 */
public record BenefitId(String value) {

    public static final int MAX_LENGTH = 64;

    public BenefitId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "value length must be <= " + MAX_LENGTH + "; got " + value.length());
        }
    }

    public static BenefitId of(String value) {
        return new BenefitId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
