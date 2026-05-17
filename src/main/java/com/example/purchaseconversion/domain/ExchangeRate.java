package com.example.purchaseconversion.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A Treasury exchange-rate record. Composite identity is
 * {@code (currency, recordDate, effectiveDate)} per ADR-0001 D-3 / A-018
 * (versioned persistence; Phase-4 G4-P1-4 closure).
 *
 * <p>The {@code rate} expresses foreign-currency-units per ONE U.S. dollar (D-4;
 * empirically verified in Phase-3 prototype: {@code amountUsd × rate = foreignAmount}).
 *
 * <p>Domain invariants:
 * <ul>
 *   <li>Every field is non-null.</li>
 *   <li>{@code rate > 0} (FR-003; AC-024b).</li>
 *   <li>{@code rate ≤ 1e30} — sanity bound widened from 10⁹ to admit hyperinflation
 *       currencies (Phase-6 G6-P1-3).</li>
 *   <li>{@code effectiveDate} is non-null (per Phase-6 G4-P1-2 the strict
 *       {@code effectiveDate >= recordDate} constraint was softened; only non-null is
 *       enforced in code, with the underlying storage layer free to record corrections
 *       however Treasury publishes them).</li>
 * </ul>
 */
public record ExchangeRate(
        CurrencyDescriptor currency,
        LocalDate recordDate,
        LocalDate effectiveDate,
        BigDecimal rate) {

    /** Upper sanity bound on a published rate (Phase-6 G6-P1-3; 10^30). */
    public static final BigDecimal MAX_RATE = new BigDecimal("1e30");

    public ExchangeRate {
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(recordDate, "recordDate must not be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("rate must be strictly positive; got " + rate);
        }
        if (rate.compareTo(MAX_RATE) > 0) {
            throw new IllegalArgumentException(
                    "rate must be <= 1e30 (sanity bound); got " + rate);
        }
    }
}
