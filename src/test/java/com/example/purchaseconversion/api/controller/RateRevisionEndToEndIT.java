package com.example.purchaseconversion.api.controller;

import com.example.purchaseconversion.infrastructure.AbstractPostgresIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * C 30-review §4.4 — AC-026b persistence-centric idempotency at the HTTP
 * boundary. The B1 IT-level test (`ExchangeRateRepoIT.VersionedUpsert.*`)
 * proves the persistence invariant; this IT proves it propagates through the
 * full HTTP stack — a rate revision that lands between two HTTP calls produces
 * a different {@code exchangeRate} for the same purchase on the second call,
 * AND the {@code exchange_rates} table holds both rows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "resilience4j.circuitbreaker.instances.treasuryClient.minimum-number-of-calls=50",
        "resilience4j.retry.instances.treasuryClient.max-attempts=1",
        "wex.ratelimit.permits-per-second=1000",
        // C2 30-review-v2 §2 — force the hot-cache to expire immediately so the second
        // GET re-runs the Treasury fetch path and actually exercises the versioned-upsert.
        // Without this, the first call's cached entry short-circuits before the revision
        // is fetched and AC-026b is not exercised at the HTTP boundary.
        "wex.cache.exchange-rate.expire-after-write-hours=0"
})
class RateRevisionEndToEndIT extends AbstractPostgresIT {

    private static WireMockServer wireMock;

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
        jdbcClient.sql("DELETE FROM exchange_rates").update();
        jdbcClient.sql("DELETE FROM purchase_transactions").update();
    }

    @DynamicPropertySource
    static void bindTreasury(DynamicPropertyRegistry r) {
        r.add("wex.treasury.base-url", () -> wireMock.baseUrl());
        r.add("wex.treasury.rates-path", () -> "/services/api/fiscal_service/v1/accounting/od/rates_of_exchange");
    }

    @Test
    @DisplayName("AC-026b — revision between two HTTP calls: both rows persist; second call sees revised rate")
    void revisionAcrossTwoCalls() throws Exception {
        // POST the purchase.
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"description":"Coffee","transactionDate":"2026-05-10","amountUsd":"100.00"}""";
        ResponseEntity<String> createResp = rest.exchange(
                "/api/v1/purchases", HttpMethod.POST, new HttpEntity<>(body, json), String.class);
        String id = objectMapper.readTree(createResp.getBody()).get("id").asText();

        // First Treasury fetch — record_date=2026-04-15, effective_date=2026-04-15, rate=1.370000
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    { "data": [
                        { "country_currency_desc": "Canada-Dollar",
                          "record_date": "2026-04-15",
                          "effective_date": "2026-04-15",
                          "exchange_rate": "1.370" }
                    ] }""")));

        ResponseEntity<String> first = rest.getForEntity(
                "/api/v1/purchases/" + id + "/conversion?currency=CAD", String.class);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        JsonNode firstTree = objectMapper.readTree(first.getBody());
        assertThat(firstTree.get("exchangeRate").asText()).isEqualTo("1.370000");
        assertThat(new BigDecimal(firstTree.get("convertedAmount").asText()))
                .isEqualByComparingTo("137.00");

        // Treasury REVISES the same (currency, record_date) with a later effective_date + new rate.
        // Per C2 30-review-v2 §2 fix spec: do NOT delete exchange_rates; leave the original 1.370
        // row in place so the second call exercises the actual versioned-upsert path. The cache
        // is expired via the @TestPropertySource hours=0 override above.
        wireMock.resetAll();
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    { "data": [
                        { "country_currency_desc": "Canada-Dollar",
                          "record_date": "2026-04-15",
                          "effective_date": "2026-04-20",
                          "exchange_rate": "1.420" }
                    ] }""")));

        ResponseEntity<String> second = rest.getForEntity(
                "/api/v1/purchases/" + id + "/conversion?currency=CAD", String.class);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        JsonNode secondTree = objectMapper.readTree(second.getBody());
        // (1) HTTP response carries the LATEST effective_date row (max(effective_date) wins).
        assertThat(secondTree.get("exchangeRate").asText())
                .as("AC-026b — second call returns the revised rate; latest effective_date wins")
                .isEqualTo("1.420000");
        assertThat(new BigDecimal(secondTree.get("convertedAmount").asText()))
                .isEqualByComparingTo("142.00");

        // (2) Both rows persist — versioned-upsert added a NEW row, did not overwrite.
        Long count = jdbcClient.sql(
                "SELECT COUNT(*) FROM exchange_rates "
                        + "WHERE country_currency_desc = 'Canada-Dollar' AND record_date = DATE '2026-04-15'")
                .query(Long.class).single();
        assertThat(count)
                .as("AC-026b — both the original and revised rows persist for (CAD, 2026-04-15)")
                .isEqualTo(2L);

        // (3) The latest effective_date row is the revision (2026-04-20), not the original.
        java.time.LocalDate latestEffective = jdbcClient.sql(
                "SELECT effective_date FROM exchange_rates "
                        + "WHERE country_currency_desc = 'Canada-Dollar' AND record_date = DATE '2026-04-15' "
                        + "ORDER BY effective_date DESC LIMIT 1")
                .query(java.time.LocalDate.class).single();
        assertThat(latestEffective).isEqualTo(java.time.LocalDate.of(2026, 4, 20));
    }
}
