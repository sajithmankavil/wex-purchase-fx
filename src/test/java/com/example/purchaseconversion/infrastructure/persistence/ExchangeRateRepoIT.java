package com.example.purchaseconversion.infrastructure.persistence;

import com.example.purchaseconversion.application.port.out.ExchangeRateHotCachePort;
import com.example.purchaseconversion.application.port.out.ExchangeRateRepositoryPort;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.domain.ExchangeRate;
import com.example.purchaseconversion.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExchangeRateRepoIT extends AbstractPostgresIT {

    private static final CurrencyDescriptor CAD = CurrencyDescriptor.of("Canada-Dollar");

    @Autowired
    private ExchangeRateRepositoryPort repo;

    @Autowired
    private ExchangeRateHotCachePort hotCache;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void clean() {
        jdbcClient.sql("DELETE FROM exchange_rates").update();
    }

    @Nested
    @DisplayName("AC-026b — versioned upsert")
    class VersionedUpsert {

        @Test
        @DisplayName("identical row revisit is a no-op (row-count unchanged)")
        void identicalRowIsNoOp() {
            ExchangeRate r = rate(CAD, "2026-04-01", "2026-04-01", "1.370000");
            repo.upsertVersioned(List.of(r));
            repo.upsertVersioned(List.of(r));

            Long count = countRows();
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-026b — new effective_date lands as a new row (row-count = 2)")
        void revisionAddsRow() {
            ExchangeRate original = rate(CAD, "2026-04-01", "2026-04-01", "1.370000");
            ExchangeRate revision = rate(CAD, "2026-04-01", "2026-04-05", "1.380000");

            repo.upsertVersioned(List.of(original));
            repo.upsertVersioned(List.of(revision));

            Long count = countRows();
            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("G4-P0-3 — Treasury scale variants are normalised to scale 6")
        void scaleSixNormalisation() {
            ExchangeRate shortScale = rate(CAD, "2026-04-02", "2026-04-02", "1.37");
            repo.upsertVersioned(List.of(shortScale));

            BigDecimal persisted = jdbcClient.sql(
                    "SELECT exchange_rate FROM exchange_rates WHERE record_date = DATE '2026-04-02'")
                    .query(BigDecimal.class).single();
            assertThat(persisted.scale()).isEqualTo(6);
            assertThat(persisted).isEqualByComparingTo("1.370000");
        }
    }

    @Nested
    @DisplayName("findInWindow")
    class FindInWindow {

        @Test
        @DisplayName("returns rates whose record_date is in the inclusive window")
        void inclusiveWindow() {
            repo.upsertVersioned(List.of(
                    rate(CAD, "2026-01-31", "2026-01-31", "1.300000"),
                    rate(CAD, "2026-04-15", "2026-04-15", "1.350000"),
                    rate(CAD, "2026-05-17", "2026-05-17", "1.370000")));

            List<ExchangeRate> hit = repo.findInWindow(
                    CAD, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 17));

            assertThat(hit).hasSize(2);
            assertThat(hit).extracting(ExchangeRate::recordDate)
                    .containsExactlyInAnyOrder(
                            LocalDate.of(2026, 4, 15), LocalDate.of(2026, 5, 17));
        }

        @Test
        @DisplayName("filters by currency")
        void filtersByCurrency() {
            CurrencyDescriptor eur = CurrencyDescriptor.of("Euro Zone-Euro");
            repo.upsertVersioned(List.of(
                    rate(CAD, "2026-04-15", "2026-04-15", "1.350000"),
                    rate(eur, "2026-04-15", "2026-04-15", "0.920000")));

            List<ExchangeRate> hit = repo.findInWindow(
                    CAD, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 17));

            assertThat(hit).hasSize(1);
            assertThat(hit.get(0).currency()).isEqualTo(CAD);
        }

        @Test
        @DisplayName("empty list when no rate is in window")
        void emptyWindow() {
            repo.upsertVersioned(List.of(rate(CAD, "2025-12-01", "2025-12-01", "1.300000")));

            List<ExchangeRate> hit = repo.findInWindow(
                    CAD, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 17));

            assertThat(hit).isEmpty();
        }
    }

    @Nested
    @DisplayName("A2 intake §2 — hot-cache upsert-invalidation contract")
    class HotCacheInvalidationContract {

        @Test
        @DisplayName("upsertVersioned invalidates the cache for every upserted (currency, recordDate) key")
        void invalidatesCacheKeys() {
            // Seed the cache with stale rows for two record_dates.
            ExchangeRate staleA = rate(CAD, "2026-04-01", "2026-04-01", "1.300000");
            ExchangeRate staleB = rate(CAD, "2026-04-15", "2026-04-15", "1.350000");
            hotCache.putAll(List.of(staleA, staleB));

            // Sanity-check the seed reached the cache via the window-scan read path.
            List<ExchangeRate> seededWindow = hotCache.findInWindow(
                    CAD, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15));
            assertThat(seededWindow).hasSize(2);

            // Persist new revisions; the contract is: cache MUST be invalidated for both keys.
            ExchangeRate revA = rate(CAD, "2026-04-01", "2026-04-10", "1.310000");
            ExchangeRate revB = rate(CAD, "2026-04-15", "2026-04-20", "1.360000");
            repo.upsertVersioned(List.of(revA, revB));

            List<ExchangeRate> afterUpsert = hotCache.findInWindow(
                    CAD, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15));
            assertThat(afterUpsert)
                    .as("hot cache must be empty for the invalidated (currency, recordDate) keys")
                    .isEmpty();
        }

        @Test
        @DisplayName("identical-row upsert still invalidates (defensive — DB may carry a later effective version)")
        void identicalUpsertStillInvalidates() {
            ExchangeRate r = rate(CAD, "2026-05-01", "2026-05-01", "1.400000");
            hotCache.putAll(List.of(r));

            repo.upsertVersioned(List.of(r));

            List<ExchangeRate> hit = hotCache.findInWindow(
                    CAD, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1));
            assertThat(hit).isEmpty();
        }
    }

    private Long countRows() {
        return jdbcClient.sql("SELECT COUNT(*) FROM exchange_rates").query(Long.class).single();
    }

    private static ExchangeRate rate(CurrencyDescriptor currency, String recordDate, String effectiveDate, String rate) {
        return new ExchangeRate(
                currency,
                LocalDate.parse(recordDate),
                LocalDate.parse(effectiveDate),
                new BigDecimal(rate));
    }
}
