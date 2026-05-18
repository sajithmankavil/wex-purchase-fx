package com.example.purchaseconversion.infrastructure.cache;

import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.domain.ExchangeRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G4-P0-2 — hot cache key is {@code (currency, recordDate)}, NOT the per-purchase lookup
 * window. Range queries pull a sub-map by record_date.
 */
class HotCacheKeyTest {

    private static final CurrencyDescriptor CAD = CurrencyDescriptor.of("Canada-Dollar");
    private static final CurrencyDescriptor EUR = CurrencyDescriptor.of("Euro Zone-Euro");

    private ExchangeRateHotCacheAdapter cache;

    @BeforeEach
    void setUp() {
        cache = new ExchangeRateHotCacheAdapter(2000L, 24L);
    }

    @Nested
    @DisplayName("Keying by (currency, recordDate)")
    class Keying {

        @Test
        @DisplayName("two purchases overlapping in window hit the SAME cached row for the same recordDate")
        void sharedKeyReused() {
            ExchangeRate r = rate(CAD, "2026-04-15", "2026-04-15", "1.350000");
            cache.putAll(List.of(r));

            // Purchase #1: window [2026-01-15, 2026-04-30] — covers 2026-04-15
            List<ExchangeRate> hit1 = cache.findInWindow(
                    CAD, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 4, 30));
            // Purchase #2: window [2026-03-01, 2026-06-30] — also covers 2026-04-15
            List<ExchangeRate> hit2 = cache.findInWindow(
                    CAD, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 30));

            assertThat(hit1).hasSize(1);
            assertThat(hit2).hasSize(1);
            assertThat(hit1.get(0)).isSameAs(hit2.get(0));
            assertThat(cache.currencyCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("different currencies live in separate inner maps")
        void differentCurrenciesIsolated() {
            cache.putAll(List.of(
                    rate(CAD, "2026-04-15", "2026-04-15", "1.350000"),
                    rate(EUR, "2026-04-15", "2026-04-15", "0.920000")));

            List<ExchangeRate> cadHit = cache.findInWindow(
                    CAD, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
            List<ExchangeRate> eurHit = cache.findInWindow(
                    EUR, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

            assertThat(cadHit).hasSize(1);
            assertThat(cadHit.get(0).currency()).isEqualTo(CAD);
            assertThat(eurHit).hasSize(1);
            assertThat(eurHit.get(0).currency()).isEqualTo(EUR);
        }
    }

    @Nested
    @DisplayName("Range query semantics")
    class RangeQuery {

        @Test
        @DisplayName("inclusive window — both endpoints hit")
        void inclusiveEndpoints() {
            cache.putAll(List.of(
                    rate(CAD, "2026-04-01", "2026-04-01", "1.300000"),
                    rate(CAD, "2026-05-31", "2026-05-31", "1.400000")));

            List<ExchangeRate> hit = cache.findInWindow(
                    CAD, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 31));

            assertThat(hit).hasSize(2);
        }

        @Test
        @DisplayName("out-of-window rates excluded")
        void outsideWindowExcluded() {
            cache.putAll(List.of(
                    rate(CAD, "2026-03-31", "2026-03-31", "1.290000"),
                    rate(CAD, "2026-04-15", "2026-04-15", "1.350000"),
                    rate(CAD, "2026-06-01", "2026-06-01", "1.410000")));

            List<ExchangeRate> hit = cache.findInWindow(
                    CAD, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 31));

            assertThat(hit).hasSize(1);
            assertThat(hit.get(0).recordDate()).isEqualTo(LocalDate.of(2026, 4, 15));
        }

        @Test
        @DisplayName("no rate in window → empty list")
        void emptyWindow() {
            cache.putAll(List.of(
                    rate(CAD, "2025-12-01", "2025-12-01", "1.250000")));

            List<ExchangeRate> hit = cache.findInWindow(
                    CAD, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 31));

            assertThat(hit).isEmpty();
        }
    }

    @Nested
    @DisplayName("Revision semantics (later effectiveDate wins on putAll merge)")
    class RevisionSemantics {

        @Test
        @DisplayName("later effective_date for same record_date REPLACES the incumbent")
        void laterEffectiveReplaces() {
            ExchangeRate older = rate(CAD, "2026-04-15", "2026-04-15", "1.350000");
            ExchangeRate revised = rate(CAD, "2026-04-15", "2026-04-20", "1.360000");
            cache.putAll(List.of(older));
            cache.putAll(List.of(revised));

            List<ExchangeRate> hit = cache.findInWindow(
                    CAD, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

            assertThat(hit).hasSize(1);
            assertThat(hit.get(0)).isEqualTo(revised);
        }

        @Test
        @DisplayName("earlier effective_date is rejected; the incumbent stays")
        void earlierEffectiveRejected() {
            ExchangeRate newer = rate(CAD, "2026-04-15", "2026-04-20", "1.360000");
            ExchangeRate older = rate(CAD, "2026-04-15", "2026-04-15", "1.350000");
            cache.putAll(List.of(newer));
            cache.putAll(List.of(older));

            List<ExchangeRate> hit = cache.findInWindow(
                    CAD, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

            assertThat(hit).hasSize(1);
            assertThat(hit.get(0)).isEqualTo(newer);
        }
    }

    @Test
    @DisplayName("invalidate drops a single (currency, recordDate) pair")
    void invalidateDropsKey() {
        cache.putAll(List.of(
                rate(CAD, "2026-04-15", "2026-04-15", "1.350000"),
                rate(CAD, "2026-05-15", "2026-05-15", "1.380000")));

        cache.invalidate(CAD, LocalDate.of(2026, 4, 15));

        List<ExchangeRate> hit = cache.findInWindow(
                CAD, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 31));

        assertThat(hit).hasSize(1);
        assertThat(hit.get(0).recordDate()).isEqualTo(LocalDate.of(2026, 5, 15));
    }

    @Test
    @DisplayName("invalidate is idempotent on a missing key")
    void invalidateIdempotent() {
        cache.invalidate(CAD, LocalDate.of(2026, 4, 15));
        // No exception; no entries created.
        assertThat(cache.findInWindow(CAD, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .isEmpty();
    }

    private static ExchangeRate rate(CurrencyDescriptor currency, String recordDate, String effectiveDate, String rate) {
        return new ExchangeRate(
                currency,
                LocalDate.parse(recordDate),
                LocalDate.parse(effectiveDate),
                new BigDecimal(rate));
    }
}
