package com.example.purchaseconversion.application.port.out;

import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.domain.ExchangeRate;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Outbound port for exchange-rate persistence (component-design.md §1, §3.2; ADR-0001 D-3).
 *
 * <p>Returns ALL rates whose {@code recordDate} falls in the given closed window. Selection of
 * the eligible rate (6-month rule + max(recordDate) + tie-break on effectiveDate) is the
 * application layer's responsibility via {@link com.example.purchaseconversion.domain.RateSelectionPolicy}.
 * The port stays pure-data-access; the policy stays a domain rule.
 *
 * <p>Versioned upsert (D-3): rows are keyed by the triple
 * {@code (currency, recordDate, effectiveDate)}. Identical rows are no-ops; new
 * {@code effectiveDate} for an existing {@code (currency, recordDate)} is persisted as an
 * additional row, never as an in-place update.
 */
public interface ExchangeRateRepositoryPort {

    /**
     * Returns all rates for {@code currency} whose {@code recordDate} is in
     * {@code [windowLower, windowUpper]} inclusive. Order is not guaranteed; selection is
     * performed by the caller via {@code RateSelectionPolicy}.
     */
    List<ExchangeRate> findInWindow(
            CurrencyDescriptor currency,
            LocalDate windowLower,
            LocalDate windowUpper);

    /**
     * Persists each rate under versioned semantics (D-3). Implementations must treat
     * identical {@code (currency, recordDate, effectiveDate, rate)} rows as no-ops.
     */
    void upsertVersioned(Collection<ExchangeRate> rates);
}
