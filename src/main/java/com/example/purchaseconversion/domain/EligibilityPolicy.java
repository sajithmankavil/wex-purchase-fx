package com.example.purchaseconversion.domain;

import java.util.Objects;

/**
 * The minimum-tier eligibility rule (eligibility-endpoint-spec.md §3.2).
 *
 * <p>Pure function: a cardholder of {@code cardholderTier} is eligible for a benefit
 * with {@code minimumTier} iff {@code cardholderTier >= minimumTier} under
 * {@link CardTier}'s declared ordering (inclusive boundary — a cardholder at exactly
 * the benefit's minimum tier is eligible).
 *
 * <p>Stateless, no I/O — mirrors {@link RateSelectionPolicy}'s shape so it's fully
 * unit-testable without mocks.
 */
public final class EligibilityPolicy {

    private EligibilityPolicy() {
        // Utility class; not instantiable.
    }

    public static boolean isEligible(CardTier cardholderTier, CardTier minimumTier) {
        Objects.requireNonNull(cardholderTier, "cardholderTier must not be null");
        Objects.requireNonNull(minimumTier, "minimumTier must not be null");
        return cardholderTier.meetsMinimum(minimumTier);
    }
}
