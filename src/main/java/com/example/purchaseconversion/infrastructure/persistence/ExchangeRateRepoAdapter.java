package com.example.purchaseconversion.infrastructure.persistence;

import com.example.purchaseconversion.application.port.out.ExchangeRateHotCachePort;
import com.example.purchaseconversion.application.port.out.ExchangeRateRepositoryPort;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.domain.ExchangeRate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * JDBC-backed implementation of {@link ExchangeRateRepositoryPort}
 * (component-design.md §1 / §3.2; ADR-0001 D-3 / D-4 / D-10).
 *
 * <h2>Persistence contract</h2>
 * <ul>
 *   <li>Versioned by {@code effective_date}: PK is the triple
 *       {@code (country_currency_desc, record_date, effective_date)}. Identical rows
 *       are idempotent no-ops via {@code MERGE} (H2 / Postgres dialect-portable form).</li>
 *   <li>All {@code exchange_rate} values are normalised to scale 6 at write time
 *       (Phase-4 G4-P0-3); re-read values are scale-6 verbatim.</li>
 *   <li>{@code source} is fixed to {@code 'TREASURY-V1'} v1; {@code fetched_at} is
 *       set to {@link Clock#instant()} at write time.</li>
 * </ul>
 *
 * <h2>Hot-cache upsert-invalidation invariant (A2 intake §2; LOW)</h2>
 *
 * <p><strong>Every upsert path MUST go through this adapter.</strong> Direct DB writes
 * bypass the cache invalidation invariant and risk stale-rate selection in
 * {@code ConversionService}. The {@link #upsertVersioned(Collection)} method calls
 * {@link ExchangeRateHotCachePort#invalidate(CurrencyDescriptor, LocalDate)} for every
 * upserted {@code (currency, recordDate)} key — including identical-row no-ops, because
 * even an effective-no-op at the DB level may have a freshly-persisted later effective
 * version that should evict the cached entry.
 *
 * <p>The corresponding port contract is restated on {@code ExchangeRateHotCachePort}'s
 * Javadoc so future contributors cannot accidentally introduce an out-of-band upsert
 * path without surfacing the invariant.
 */
@Repository
public class ExchangeRateRepoAdapter implements ExchangeRateRepositoryPort {

    /** Scale-6 normalisation per ADR-0001 D-4 / D-10 / G4-P0-3. */
    private static final int PERSISTED_SCALE = 6;

    /** v1 provenance marker (data-model.md §4). */
    private static final String SOURCE_V1 = "TREASURY-V1";

    /**
     * Dialect-portable upsert via {@code MERGE INTO ... USING (VALUES (...)) ...}.
     * Works on H2 (default settings) and PostgreSQL 15+. For older Postgres,
     * {@code INSERT ... ON CONFLICT DO NOTHING} would be a profile-specific
     * alternative; deferred to operator preference.
     */
    private static final String UPSERT_SQL = """
            MERGE INTO exchange_rates AS target
            USING (VALUES (
                CAST(:countryCurrencyDesc AS VARCHAR(64)),
                CAST(:recordDate AS DATE),
                CAST(:effectiveDate AS DATE),
                CAST(:exchangeRate AS DECIMAL(19,6)),
                CAST(:source AS VARCHAR(32)),
                CAST(:fetchedAt AS TIMESTAMP)
            )) AS incoming (
                country_currency_desc, record_date, effective_date,
                exchange_rate, source, fetched_at
            )
            ON target.country_currency_desc = incoming.country_currency_desc
               AND target.record_date = incoming.record_date
               AND target.effective_date = incoming.effective_date
            WHEN NOT MATCHED THEN INSERT (
                country_currency_desc, record_date, effective_date,
                exchange_rate, source, fetched_at
            ) VALUES (
                incoming.country_currency_desc, incoming.record_date, incoming.effective_date,
                incoming.exchange_rate, incoming.source, incoming.fetched_at
            )
            """;

    private static final String FIND_IN_WINDOW_SQL = """
            SELECT country_currency_desc, record_date, effective_date, exchange_rate
              FROM exchange_rates
             WHERE country_currency_desc = :currency
               AND record_date BETWEEN :lower AND :upper
            """;

    private final JdbcClient jdbcClient;
    private final ExchangeRateHotCachePort hotCache;
    private final Clock clock;

    public ExchangeRateRepoAdapter(
            JdbcClient jdbcClient,
            ExchangeRateHotCachePort hotCache,
            Clock clock) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        this.hotCache = Objects.requireNonNull(hotCache, "hotCache must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public List<ExchangeRate> findInWindow(
            CurrencyDescriptor currency, LocalDate windowLower, LocalDate windowUpper) {
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(windowLower, "windowLower must not be null");
        Objects.requireNonNull(windowUpper, "windowUpper must not be null");

        return jdbcClient.sql(FIND_IN_WINDOW_SQL)
                .param("currency", currency.value())
                .param("lower", windowLower)
                .param("upper", windowUpper)
                .query((rs, rowNum) -> new ExchangeRate(
                        CurrencyDescriptor.of(rs.getString("country_currency_desc")),
                        rs.getObject("record_date", LocalDate.class),
                        rs.getObject("effective_date", LocalDate.class),
                        rs.getBigDecimal("exchange_rate")))
                .list();
    }

    @Override
    public void upsertVersioned(Collection<ExchangeRate> rates) {
        Objects.requireNonNull(rates, "rates must not be null");
        if (rates.isEmpty()) {
            return;
        }
        Instant fetchedAt = clock.instant();
        List<InvalidationKey> invalidations = new ArrayList<>(rates.size());

        for (ExchangeRate rate : rates) {
            BigDecimal scale6 = normalise(rate.rate());
            jdbcClient.sql(UPSERT_SQL)
                    .param("countryCurrencyDesc", rate.currency().value())
                    .param("recordDate", rate.recordDate())
                    .param("effectiveDate", rate.effectiveDate())
                    .param("exchangeRate", scale6)
                    .param("source", SOURCE_V1)
                    .param("fetchedAt", fetchedAt)
                    .update();
            invalidations.add(new InvalidationKey(rate.currency(), rate.recordDate()));
        }

        // A2 intake §2 hot-cache invalidation. Invalidate every upserted
        // (currency, recordDate) key — including identical-row no-ops, because the
        // DB may carry a later effective version that should evict the cache entry
        // for that key.
        for (InvalidationKey key : invalidations) {
            hotCache.invalidate(key.currency(), key.recordDate());
        }
    }

    /**
     * Normalise to scale 6 using HALF_UP. Treasury publishes variable-scale
     * decimal strings; we always persist at scale 6 (G4-P0-3).
     */
    private static BigDecimal normalise(BigDecimal rate) {
        if (rate.scale() == PERSISTED_SCALE) {
            return rate;
        }
        return rate.setScale(PERSISTED_SCALE, RoundingMode.HALF_UP);
    }

    private record InvalidationKey(CurrencyDescriptor currency, LocalDate recordDate) {}
}
