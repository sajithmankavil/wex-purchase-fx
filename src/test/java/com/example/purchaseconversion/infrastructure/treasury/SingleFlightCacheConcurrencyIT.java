package com.example.purchaseconversion.infrastructure.treasury;

import com.example.purchaseconversion.application.port.out.ExchangeRateRepositoryPort;
import com.example.purchaseconversion.application.port.out.TreasuryClientPort;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.domain.ExchangeRate;
import com.example.purchaseconversion.infrastructure.AbstractPostgresIT;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-027b/c/d/e — single-flight dedup + consistent outcomes against a WireMock-
 * backed Treasury. The single-flight gate's loser-polls-DB pattern is the
 * load-bearing semantic; this IT verifies it end-to-end through the full
 * adapter + repository stack.
 */
@SpringBootTest
@TestPropertySource(properties = {
        // Wide CB so the test can drive multiple Treasury calls without tripping it
        "resilience4j.circuitbreaker.instances.treasuryClient.minimum-number-of-calls=50",
        "resilience4j.retry.instances.treasuryClient.max-attempts=1"
})
class SingleFlightCacheConcurrencyIT extends AbstractPostgresIT {

    private static final CurrencyDescriptor CAD = CurrencyDescriptor.of("Canada-Dollar");
    private static final LocalDate APR_15 = LocalDate.of(2026, 4, 15);
    private static final LocalDate JUN_01 = LocalDate.of(2026, 6, 1);

    private static WireMockServer wireMock;

    @Autowired
    private TreasuryClientPort treasuryClient;

    @Autowired
    private ExchangeRateRepositoryPort exchangeRateRepository;

    @Autowired
    private JdbcClient jdbcClient;

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
    void resetState() {
        wireMock.resetAll();
        jdbcClient.sql("DELETE FROM exchange_rates").update();
    }

    @DynamicPropertySource
    static void bindTreasury(DynamicPropertyRegistry r) {
        r.add("wex.treasury.base-url", () -> wireMock.baseUrl());
        r.add("wex.treasury.rates-path", () -> "/services/api/fiscal_service/v1/accounting/od/rates_of_exchange");
    }

    @Test
    @DisplayName("AC-027b — three concurrent identical requests trigger exactly one Treasury call")
    void threeConcurrentRequestsOneCall() throws Exception {
        // Treasury responds after 800ms — gives losers time to enter the gate.
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(800)
                .withHeader("Content-Type", "application/json")
                .withBody(body("\"1.370\""))));

        ExecutorService exec = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<List<ExchangeRate>> f1 = exec.submit(() -> { start.await(); return fetch(APR_15); });
            Future<List<ExchangeRate>> f2 = exec.submit(() -> { start.await(); return fetch(APR_15); });
            Future<List<ExchangeRate>> f3 = exec.submit(() -> { start.await(); return fetch(APR_15); });

            start.countDown();
            // All three should complete within a few seconds.
            f1.get(15, TimeUnit.SECONDS);
            f2.get(15, TimeUnit.SECONDS);
            f3.get(15, TimeUnit.SECONDS);

            wireMock.verify(1, anyRequestedFor(anyUrl()));

            List<ExchangeRate> persisted = exchangeRateRepository.findInWindow(
                    CAD, APR_15.minusMonths(6), APR_15);
            assertThat(persisted).hasSize(1);
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    @DisplayName("AC-027e — two purchases in the same quarter share the gate (one call)")
    void quarterDeduplication() throws Exception {
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(800)
                .withHeader("Content-Type", "application/json")
                .withBody(body("\"1.370\""))));

        // Both APR_15 and JUN_01 are in Q2-2026 → same gate key.
        ExecutorService exec = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<List<ExchangeRate>> f1 = exec.submit(() -> { start.await(); return fetch(APR_15); });
            Future<List<ExchangeRate>> f2 = exec.submit(() -> { start.await(); return fetch(JUN_01); });
            start.countDown();
            f1.get(15, TimeUnit.SECONDS);
            f2.get(15, TimeUnit.SECONDS);

            wireMock.verify(1, anyRequestedFor(anyUrl()));
        } finally {
            exec.shutdownNow();
        }
    }

    private List<ExchangeRate> fetch(LocalDate transactionDate) {
        LocalDate lower = transactionDate.minusMonths(6);
        return treasuryClient.fetchRates(CAD, lower, transactionDate);
    }

    private String body(String rateJsonValue) {
        return """
                { "data": [
                    { "country_currency_desc": "Canada-Dollar",
                      "record_date": "2026-04-15",
                      "effective_date": "2026-04-15",
                      "exchange_rate": %s }
                ] }""".formatted(rateJsonValue);
    }
}
