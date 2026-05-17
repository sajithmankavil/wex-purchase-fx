package com.example.purchaseconversion.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * The 6-month rate-selection rule from FR-003 and ADR-0001 D-5.
 *
 * <p>Pure function: given a transaction date, a target currency, and a set of candidate
 * {@link ExchangeRate} rows, return the eligible rate or empty.
 *
 * <p><strong>Selection rule:</strong>
 * <pre>
 *   eligibleWindow = [ transactionDate.minusMonths(6) (Java's LocalDate EOM-clamp semantics),
 *                      transactionDate ]    // inclusive both ends
 *
 *   from rates filtered to:
 *     rate.currency       == currency
 *     rate.recordDate     in eligibleWindow
 *
 *   choose max recordDate, then max effectiveDate as tie-break (OQ-002 closure;
 *   the composite PK in storage already guarantees uniqueness, so this orders deterministically).
 * </pre>
 *
 * <p>Acceptance criteria mapped: AC-014, AC-015, AC-016, AC-017, AC-018, AC-018b, AC-019,
 * AC-019b, AC-020.
 *
 * <p>Notes:
 * <ul>
 *   <li>The window is computed via {@link LocalDate#minusMonths(long)}, which applies
 *       Java's standard end-of-month clamp (e.g., {@code 2026-08-31.minusMonths(6) → 2026-02-28}).
 *       The window length therefore varies 178–187 days across the calendar (A-003; ADR-0001 D-5).</li>
 *   <li>This class is intentionally stateless; no Clock, no I/O.</li>
 * </ul>
 */
public final class RateSelectionPolicy {

    /** Source-rule lookback period: "within the last 6 months" (FR-003). */
    public static final int LOOKBACK_MONTHS = 6;

    private RateSelectionPolicy() {
        // Utility class; not instantiable.
    }

    /**
     * Selects the eligible {@link ExchangeRate} for the given purchase date and currency,
     * or {@link Optional#empty()} if no eligible rate exists in the window.
     *
     * @param transactionDate non-null purchase date
     * @param currency        non-null target currency (canonical descriptor)
     * @param candidates      non-null collection of candidate rates; may include rates for
     *                        other currencies or out-of-window — they are filtered out
     * @return the eligible rate (max {@code recordDate}, then max {@code effectiveDate}),
     *         or empty if none qualifies
     */
    public static Optional<ExchangeRate> selectEligibleRate(
            LocalDate transactionDate,
            CurrencyDescriptor currency,
            Collection<ExchangeRate> candidates) {
        Objects.requireNonNull(transactionDate, "transactionDate must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");

        LocalDate windowLower = transactionDate.minusMonths(LOOKBACK_MONTHS);

        return candidates.stream()
                .filter(r -> r.currency().equals(currency))
                .filter(r -> isInClosedRange(r.recordDate(), windowLower, transactionDate))
                .max(Comparator
                        .comparing(ExchangeRate::recordDate)
                        .thenComparing(ExchangeRate::effectiveDate));
    }

    /** Inclusive on both ends: {@code lower <= value <= upper}. */
    private static boolean isInClosedRange(LocalDate value, LocalDate lower, LocalDate upper) {
        return !value.isBefore(lower) && !value.isAfter(upper);
    }
}
