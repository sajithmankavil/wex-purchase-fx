package com.example.purchaseconversion.infrastructure.persistence;

import com.example.purchaseconversion.domain.BenefitEligibility;
import com.example.purchaseconversion.domain.BenefitId;
import com.example.purchaseconversion.domain.CardTier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

/**
 * JDBC-backed reader of {@code benefit_eligibility} (eligibility-endpoint-spec.md §3.2).
 *
 * <p>Unlike {@code PurchaseRepositoryPort}, this is not exposed as an
 * {@code application.port.out} interface — nothing in the application layer reads
 * from the database directly for this feature (spec §4.1: all requests are served
 * from the in-memory cache). This adapter is a purely infrastructure-internal
 * collaborator of {@code BenefitEligibilityCacheAdapter}, which uses it only for
 * the initial load and periodic background refresh.
 */
@Repository
public class BenefitEligibilityRepoAdapter {

    private static final String FIND_ALL_SQL = """
            SELECT benefit_id, minimum_tier
              FROM benefit_eligibility
            """;

    private final JdbcClient jdbcClient;

    public BenefitEligibilityRepoAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
    }

    public List<BenefitEligibility> findAll() {
        return jdbcClient.sql(FIND_ALL_SQL)
                .query((rs, rowNum) -> new BenefitEligibility(
                        BenefitId.of(rs.getString("benefit_id")),
                        CardTier.valueOf(rs.getString("minimum_tier"))))
                .list();
    }
}
