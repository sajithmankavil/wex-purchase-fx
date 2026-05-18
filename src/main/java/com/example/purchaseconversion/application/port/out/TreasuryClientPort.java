package com.example.purchaseconversion.application.port.out;

import com.example.purchaseconversion.application.exception.UpstreamBadResponseException;
import com.example.purchaseconversion.application.exception.UpstreamUnavailableException;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.domain.ExchangeRate;

import java.time.LocalDate;
import java.util.List;

/**
 * Outbound port for fetching exchange rates from the U.S. Treasury Fiscal Data API
 * (ADR-0001 D-9; component-design.md §3.2).
 *
 * <p>The adapter implementation wraps an HTTP call with Resilience4j (timeout, retry,
 * circuit breaker, bulkhead), a JSON schema validator, a per-rate sanity check (rate > 0
 * and ≤ 1e30 per Phase-6 G6-P1-3), the per-{@code (currency, treasury_quarter_end)}
 * single-flight gate, and the rate-orientation contract check. None of those concerns
 * are visible at this port — the application layer sees a simple "fetch rates" contract.
 *
 * <p>Returns an empty list if Treasury responded successfully with no rates in the window
 * (AC-020b / AC-022b). Returns non-empty if rates were fetched and pass the sanity gates;
 * the caller is responsible for persisting them.
 */
public interface TreasuryClientPort {

    /**
     * Fetches exchange rates for {@code currency} whose {@code recordDate} is in
     * {@code [windowLower, windowUpper]} inclusive.
     *
     * @throws UpstreamUnavailableException  on timeout / circuit-open / 5xx after retry
     *                                       budget / single-flight loser timeout (D-9)
     * @throws UpstreamBadResponseException  on schema-invalid response or rate-sanity
     *                                       failure (AC-024 / AC-024b)
     */
    List<ExchangeRate> fetchRates(
            CurrencyDescriptor currency,
            LocalDate windowLower,
            LocalDate windowUpper);
}
