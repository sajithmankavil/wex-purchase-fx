package com.example.purchaseconversion.infrastructure.persistence;

import com.example.purchaseconversion.domain.Money;
import com.example.purchaseconversion.domain.Purchase;
import com.example.purchaseconversion.domain.PurchaseId;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-010 — durability across restart with the H2 file-mode profile.
 *
 * <p>Uses a {@link TempDir} so the test is hermetic — no leakage into the developer's
 * {@code WEX_DATA_DIR}. Connection pool is opened, schema applied, row written,
 * pool closed; a second pool against the same file URL must see the row.
 */
class DurabilityRestartIT {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("AC-010 — purchase persists across pool close + reopen against the same H2 file")
    void purchasePersistsAcrossRestart() throws Exception {
        String url = "jdbc:h2:file:" + tempDir.resolve("wex").toAbsolutePath()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";

        PurchaseId id = PurchaseId.next();
        Purchase original = new Purchase(id, "Coffee", LocalDate.of(2026, 5, 10), Money.of("4.50"));

        // ---- Round 1: open pool, migrate, save ----
        try (HikariDataSource ds = newPool(url)) {
            migrate(ds);
            PurchaseRepoAdapter repo = new PurchaseRepoAdapter(
                    JdbcClient.create(ds), Clock.systemUTC());
            repo.save(original);
        }

        // ---- Round 2: reopen pool against the same file, schema already applied, read back ----
        try (HikariDataSource ds = newPool(url)) {
            // Liquibase is idempotent — running it twice is a no-op on the same DB.
            migrate(ds);
            PurchaseRepoAdapter repo = new PurchaseRepoAdapter(
                    JdbcClient.create(ds), Clock.systemUTC());
            assertThat(repo.findById(id))
                    .as("AC-010 — purchase persisted across pool close + reopen")
                    .isPresent()
                    .get()
                    .satisfies(p -> {
                        assertThat(p.description()).isEqualTo("Coffee");
                        assertThat(p.transactionDate()).isEqualTo(LocalDate.of(2026, 5, 10));
                        assertThat(p.amountUsd()).isEqualTo(Money.of("4.50"));
                    });
        }
    }

    private HikariDataSource newPool(String url) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(1);
        return ds;
    }

    private void migrate(HikariDataSource ds) throws Exception {
        try (Connection c = ds.getConnection()) {
            Database db = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(c));
            try (Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(),
                    db)) {
                liquibase.update("");
            }
        }
    }
}
