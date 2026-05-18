package com.example.purchaseconversion.application.port.out;

import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.domain.ExchangeRate;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Outbound port for the in-memory hot-path exchange-rate cache
 * (ADR-0001 D-10; component-design.md §1, §3.2).
 *
 * <p>Implemented by {@code ExchangeRateHotCache} in {@code infrastructure.cache} (Chunk B)
 * over Caffeine. The cache is keyed by {@code (currency, recordDate)} per D-10;
 * a window lookup is implemented as a scan filtered to entries whose {@code recordDate}
 * falls in {@code [windowLower, windowUpper]}.
 *
 * <p>Eviction is silent: a cache miss is correct behaviour — the next layer (DB or
 * Treasury) is consulted. The cache is rates-only; conversion results are never cached.
 *
 * <h2>Upsert-invalidation invariant (A2 intake §2 carried into B1)</h2>
 *
 * <p>Every persistence upsert MUST go through {@code ExchangeRateRepoAdapter}, which
 * calls {@link #invalidate(CurrencyDescriptor, LocalDate)} for every upserted
 * {@code (currency, recordDate)} key. Direct DB writes that bypass that adapter risk
 * stale-rate selection in {@code ConversionService} because the service treats a
 * non-empty {@link #findInWindow} result as authoritative. A future contributor
 * introducing a second upsert path must extend the invariant — or grow a
 * {@code bulkInvalidate(CurrencyDescriptor)} on this port and call it from the new path.
 */
public interface ExchangeRateHotCachePort {

    /**
     * Returns all cached rates for {@code currency} whose {@code recordDate} is in
     * {@code [windowLower, windowUpper]} inclusive. Empty list on miss.
     */
    List<ExchangeRate> findInWindow(
            CurrencyDescriptor currency,
            LocalDate windowLower,
            LocalDate windowUpper);

    /**
     * Inserts or refreshes the given rates in the cache, keyed by
     * {@code (currency, recordDate)} per D-10. Existing entries with the same key are
     * replaced (Treasury revision semantics — newer {@code effectiveDate} wins).
     */
    void putAll(Collection<ExchangeRate> rates);

    /**
     * Invalidates the cached entry for {@code (currency, recordDate)}. Idempotent —
     * calling on a key not present is a no-op.
     */
    void invalidate(CurrencyDescriptor currency, LocalDate recordDate);
}
