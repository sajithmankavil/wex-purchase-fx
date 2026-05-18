package com.example.purchaseconversion.infrastructure.persistence;

import com.example.purchaseconversion.infrastructure.AbstractPostgresIT;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Liquibase changelog applies cleanly + rolls back cleanly against
 * Postgres (rollback class C per rollback-plan.md §4.4).
 *
 * <p>Drill:
 * <ol>
 *   <li>Fresh Postgres container starts empty.</li>
 *   <li>{@code update} applies both changesets; both tables exist; index present.</li>
 *   <li>{@code rollback count=2} reverts both changesets; both tables gone.</li>
 *   <li>{@code update} re-applies cleanly — proves the rollback is reversible.</li>
 * </ol>
 *
 * <p>Does NOT extend {@link AbstractPostgresIT} because we need direct Liquibase
 * lifecycle control without the Spring context's auto-applied migrations. Spins up
 * its own Postgres testcontainer.
 */
class LiquibaseMigrationIT {

    @Test
    @DisplayName("apply → rollback → re-apply, against a fresh Postgres")
    void rollbackDrill() throws Exception {
        try (org.testcontainers.containers.PostgreSQLContainer<?> pg =
                     new org.testcontainers.containers.PostgreSQLContainer<>("postgres:16-alpine")
                             .withDatabaseName("wex").withUsername("wex").withPassword("wex")) {
            pg.start();
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(pg.getJdbcUrl());
            ds.setUsername(pg.getUsername());
            ds.setPassword(pg.getPassword());

            // 1. Apply.
            withLiquibase(ds, lb -> lb.update(""));
            assertTableExists(ds, "purchase_transactions");
            assertTableExists(ds, "exchange_rates");
            assertIndexExists(ds, "ix_exchange_rates_lookup");

            // 2. Rollback.
            withLiquibase(ds, lb -> lb.rollback(2, ""));
            assertTableMissing(ds, "purchase_transactions");
            assertTableMissing(ds, "exchange_rates");

            // 3. Re-apply.
            withLiquibase(ds, lb -> lb.update(""));
            assertTableExists(ds, "purchase_transactions");
            assertTableExists(ds, "exchange_rates");

            ds.close();
        }
    }

    private interface LiquibaseAction {
        void run(Liquibase liquibase) throws Exception;
    }

    private void withLiquibase(HikariDataSource ds, LiquibaseAction action) throws Exception {
        try (Connection c = ds.getConnection()) {
            Database db = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(c));
            try (Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(),
                    db)) {
                action.run(liquibase);
            }
        }
    }

    private void assertTableExists(HikariDataSource ds, String table) throws Exception {
        boolean exists = lookupTable(ds, table);
        assertThat(exists).as("table %s should exist", table).isTrue();
    }

    private void assertTableMissing(HikariDataSource ds, String table) throws Exception {
        boolean exists = lookupTable(ds, table);
        assertThat(exists).as("table %s should NOT exist after rollback", table).isFalse();
    }

    private boolean lookupTable(HikariDataSource ds, String table) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT to_regclass(?) IS NOT NULL")) {
            ps.setString(1, "public." + table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private void assertIndexExists(HikariDataSource ds, String index) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM pg_indexes WHERE indexname = ?")) {
            ps.setString(1, index);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).as("index %s should exist", index).isEqualTo(1);
            }
        }
    }
}
