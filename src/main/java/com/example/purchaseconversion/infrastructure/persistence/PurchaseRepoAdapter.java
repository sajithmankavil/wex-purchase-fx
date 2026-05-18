package com.example.purchaseconversion.infrastructure.persistence;

import com.example.purchaseconversion.application.port.out.PurchaseRepositoryPort;
import com.example.purchaseconversion.domain.Money;
import com.example.purchaseconversion.domain.Purchase;
import com.example.purchaseconversion.domain.PurchaseId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC-backed implementation of {@link PurchaseRepositoryPort}
 * (component-design.md §1 / §3.1; ADR-0001 D-2 — JdbcClient, NO JPA / Hibernate).
 *
 * <p>Maps {@link Purchase} ↔ {@code purchase_transactions} row per data-model.md §3.
 * The {@code id} column stores the UUID v7 canonical string form;
 * {@code amount_usd} is DECIMAL(19,2); {@code created_at}/{@code updated_at} are
 * UTC TIMESTAMP(6) populated by the application clock (no DB defaults so the
 * value is deterministic in tests).
 */
@Repository
public class PurchaseRepoAdapter implements PurchaseRepositoryPort {

    private static final String INSERT_SQL = """
            INSERT INTO purchase_transactions
                (id, description, transaction_date, amount_usd, created_at, updated_at)
            VALUES (:id, :description, :transactionDate, :amountUsd, :createdAt, :updatedAt)
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT id, description, transaction_date, amount_usd
              FROM purchase_transactions
             WHERE id = :id
            """;

    private final JdbcClient jdbcClient;
    private final Clock clock;

    public PurchaseRepoAdapter(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Purchase save(Purchase purchase) {
        Objects.requireNonNull(purchase, "purchase must not be null");
        Instant now = clock.instant();
        jdbcClient.sql(INSERT_SQL)
                .param("id", purchase.id().toString())
                .param("description", purchase.description())
                .param("transactionDate", purchase.transactionDate())
                .param("amountUsd", purchase.amountUsd().value())
                .param("createdAt", now)
                .param("updatedAt", now)
                .update();
        return purchase;
    }

    @Override
    public Optional<Purchase> findById(PurchaseId id) {
        Objects.requireNonNull(id, "id must not be null");
        return jdbcClient.sql(FIND_BY_ID_SQL)
                .param("id", id.toString())
                .query((rs, rowNum) -> new Purchase(
                        PurchaseId.fromString(rs.getString("id")),
                        rs.getString("description"),
                        rs.getObject("transaction_date", LocalDate.class),
                        Money.of(rs.getBigDecimal("amount_usd"))))
                .optional();
    }
}
