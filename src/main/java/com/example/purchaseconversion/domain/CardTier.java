package com.example.purchaseconversion.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Cardholder tier for the benefit-eligibility check (eligibility-endpoint-spec.md §3.1).
 *
 * <p>Ordered {@code PLATINUM < SIGNATURE < INFINITE} via declaration order — this
 * ordering is a locked-in assumption of the spec (§5 blocking #2), not an inferred
 * detail; every eligibility comparison in this service depends on it.
 */
public enum CardTier {
    PLATINUM,
    SIGNATURE,
    INFINITE;

    /**
     * Parses the canonical wire form (exact enum name, case-sensitive per spec §2).
     * Returns empty rather than throwing — callers decide how "unknown tier" maps
     * to a response (spec §3.3: 400 {@code INVALID_TIER}).
     */
    public static Optional<CardTier> parse(String raw) {
        Objects.requireNonNull(raw, "raw must not be null");
        for (CardTier tier : values()) {
            if (tier.name().equals(raw)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    /** True iff this tier meets or exceeds {@code minimum} (inclusive boundary, spec §3.2). */
    public boolean meetsMinimum(CardTier minimum) {
        Objects.requireNonNull(minimum, "minimum must not be null");
        return this.ordinal() >= minimum.ordinal();
    }
}
