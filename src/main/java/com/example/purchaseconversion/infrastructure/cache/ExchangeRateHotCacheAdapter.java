package com.example.purchaseconversion.infrastructure.cache;

import com.example.purchaseconversion.application.port.out.ExchangeRateHotCachePort;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.domain.ExchangeRate;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Caffeine-backed implementation of {@link ExchangeRateHotCachePort}
 * (ADR-0001 D-10; G4-P0-2).
 *
 * <h2>Topology</h2>
 *
 * <p>Two-level structure for O(log n) range queries:
 * <ul>
 *   <li><b>Outer:</b> Caffeine cache keyed by {@link CurrencyDescriptor}, holding a
 *       per-currency {@link ConcurrentNavigableMap}; {@code expireAfterWrite} TTL
 *       applies to the per-currency map, not individual rows.</li>
 *   <li><b>Inner:</b> per-currency {@link ConcurrentSkipListMap}{@code <LocalDate,
 *       ExchangeRate>} keyed by {@code recordDate}; supports
 *       {@link ConcurrentNavigableMap#subMap subMap}-based window queries.</li>
 * </ul>
 *
 * <p>The inner map's value is the highest-{@code effectiveDate} {@link ExchangeRate}
 * known for that {@code (currency, recordDate)} key — exactly the row the
 * eligibility policy would pick after the tie-break. {@link #putAll} merges new
 * rates: a later {@code effectiveDate} for an existing {@code (currency, recordDate)}
 * replaces the previous entry; an earlier or equal {@code effectiveDate} is skipped.
 *
 * <p>{@link #invalidate(CurrencyDescriptor, LocalDate)} drops a single
 * {@code (currency, recordDate)} pair from the inner map. Used by
 * {@code ExchangeRateRepoAdapter.upsertVersioned(...)} per the
 * A2 intake §2 contract.
 */
@Component
public class ExchangeRateHotCacheAdapter implements ExchangeRateHotCachePort {

    private final Cache<CurrencyDescriptor, ConcurrentNavigableMap<LocalDate, ExchangeRate>> outer;

    public ExchangeRateHotCacheAdapter(
            @Value("${wex.cache.exchange-rate.maximum-size:2000}") long maximumSize,
            @Value("${wex.cache.exchange-rate.expire-after-write-hours:24}") long expireAfterWriteHours) {
        this.outer = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(Duration.ofHours(expireAfterWriteHours))
                .build();
    }

    @Override
    public List<ExchangeRate> findInWindow(
            CurrencyDescriptor currency, LocalDate windowLower, LocalDate windowUpper) {
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(windowLower, "windowLower must not be null");
        Objects.requireNonNull(windowUpper, "windowUpper must not be null");

        ConcurrentNavigableMap<LocalDate, ExchangeRate> inner = outer.getIfPresent(currency);
        if (inner == null || inner.isEmpty()) {
            return List.of();
        }
        // subMap is [from, to] when both inclusive flags are true.
        ConcurrentNavigableMap<LocalDate, ExchangeRate> window =
                inner.subMap(windowLower, true, windowUpper, true);
        if (window.isEmpty()) {
            return List.of();
        }
        return List.copyOf(window.values());
    }

    @Override
    public void putAll(Collection<ExchangeRate> rates) {
        Objects.requireNonNull(rates, "rates must not be null");
        if (rates.isEmpty()) {
            return;
        }
        // Group by currency to amortise outer-map lookups.
        Map<CurrencyDescriptor, List<ExchangeRate>> byCurrency = new ConcurrentHashMap<>();
        for (ExchangeRate rate : rates) {
            byCurrency.computeIfAbsent(rate.currency(), c -> new java.util.ArrayList<>()).add(rate);
        }
        for (Map.Entry<CurrencyDescriptor, List<ExchangeRate>> entry : byCurrency.entrySet()) {
            ConcurrentNavigableMap<LocalDate, ExchangeRate> inner = outer.get(
                    entry.getKey(), c -> new ConcurrentSkipListMap<>());
            for (ExchangeRate fresh : entry.getValue()) {
                inner.merge(fresh.recordDate(), fresh, ExchangeRateHotCacheAdapter::keepLaterEffective);
            }
        }
    }

    @Override
    public void invalidate(CurrencyDescriptor currency, LocalDate recordDate) {
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(recordDate, "recordDate must not be null");
        ConcurrentNavigableMap<LocalDate, ExchangeRate> inner = outer.getIfPresent(currency);
        if (inner != null) {
            inner.remove(recordDate);
        }
    }

    /** Visible for testing — expose current outer-map size for HotCacheKeyTest. */
    long currencyCount() {
        return outer.estimatedSize();
    }

    /** Visible for testing — view of the inner map for diagnostics. */
    Map<LocalDate, ExchangeRate> innerView(CurrencyDescriptor currency) {
        ConcurrentNavigableMap<LocalDate, ExchangeRate> inner = outer.getIfPresent(currency);
        return inner == null ? Collections.emptyMap() : Collections.unmodifiableMap(inner);
    }

    private static ExchangeRate keepLaterEffective(ExchangeRate incumbent, ExchangeRate fresh) {
        // Treasury revision semantics — later effectiveDate wins; equal effectiveDate keeps
        // the incumbent (idempotent put).
        return fresh.effectiveDate().isAfter(incumbent.effectiveDate()) ? fresh : incumbent;
    }
}
