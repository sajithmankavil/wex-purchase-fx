package com.example.purchaseconversion.application.exception;

import java.util.Objects;

/**
 * Thrown when the {@code tier} query parameter does not match a known
 * {@link com.example.purchaseconversion.domain.CardTier}. Maps to HTTP 400 with
 * {@code errorCode = INVALID_TIER} (eligibility-endpoint-spec.md §2, §3.3).
 *
 * <p>Unlike {@code MalformedIdentifierException} / {@code InvalidCurrencyException},
 * the raw value is echoed directly (not hashed) — {@code tier} is a short,
 * bounded-enum-shaped field with no PAN-disclosure risk, unlike the free-text
 * fields those exceptions guard.
 */
public final class InvalidTierException extends DomainException {

    private final String tier;

    public InvalidTierException(String tier) {
        super("unrecognized card tier: " + Objects.requireNonNull(tier));
        this.tier = tier;
    }

    public String getTier() {
        return tier;
    }
}
