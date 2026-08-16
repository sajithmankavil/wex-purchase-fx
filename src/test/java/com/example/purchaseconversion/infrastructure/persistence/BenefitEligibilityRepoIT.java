package com.example.purchaseconversion.infrastructure.persistence;

import com.example.purchaseconversion.domain.BenefitEligibility;
import com.example.purchaseconversion.domain.BenefitId;
import com.example.purchaseconversion.domain.CardTier;
import com.example.purchaseconversion.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * eligibility-endpoint-spec.md §3.2, §7 — confirms the Liquibase migration + seed
 * data + JDBC read all agree against a real Postgres.
 */
@SpringBootTest
class BenefitEligibilityRepoIT extends AbstractPostgresIT {

    @Autowired
    private BenefitEligibilityRepoAdapter repository;

    @Test
    @DisplayName("v3 migration seeds the three illustrative benefits spanning all tiers")
    void readsSeedData() {
        List<BenefitEligibility> all = repository.findAll();

        assertThat(all).containsExactlyInAnyOrder(
                new BenefitEligibility(BenefitId.of("BEN-1001"), CardTier.PLATINUM),
                new BenefitEligibility(BenefitId.of("BEN-1002"), CardTier.SIGNATURE),
                new BenefitEligibility(BenefitId.of("BEN-1003"), CardTier.INFINITE));
    }
}
