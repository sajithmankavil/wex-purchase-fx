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
 * C 30-review §4.3 — AC-T-3 widened fixture suite at the FULL HTTP-in →
 * controller → application service → adapter → WireMock-backed Treasury-out
 * stack. Complements the adapter-scoped {@code TreasuryClientIT} from B2 by
 * proving the HTTP envelope translates correctly end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "resilience4j.circuitbreaker.instances.treasuryClient.minimum-number-of-calls=50",
        "resilience4j.retry.instances.treasuryClient.max-attempts=1",
        "wex.ratelimit.permits-per-second=1000"
})
class EndToEndTreasuryIT extends AbstractPostgresIT {

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
    @DisplayName("AC-T-3 end-to-end — happy path: POST purchase → GET conversion → scale-6 exchangeRate")
    void endToEndHappy() throws Exception {
        // 1. WireMock stubs Treasury with a Luhn-style scale-3 rate that normalises to scale 6.
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

        // 2. POST a purchase.
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"description":"Coffee","transactionDate":"2026-05-10","amountUsd":"123.45"}""";
        ResponseEntity<String> createResp = rest.exchange(
                "/api/v1/purchases", HttpMethod.POST, new HttpEntity<>(body, json), String.class);
        assertThat(createResp.getStatusCode().value()).isEqualTo(201);

        String id = extractField(createResp.getBody(), "id");

        // 3. GET conversion. Adapter fetches from WireMock, persists scale-6, returns scale-6.
        ResponseEntity<String> convResp = rest.getForEntity(
                "/api/v1/purchases/" + id + "/conversion?currency=CAD", String.class);
        assertThat(convResp.getStatusCode().value()).isEqualTo(200);

        JsonNode tree = objectMapper.readTree(convResp.getBody());
        assertThat(tree.get("exchangeRate").asText()).isEqualTo("1.370000");
        assertThat(tree.get("targetCurrency").asText()).isEqualTo("Canada-Dollar");
        // 123.45 * 1.370 = 169.1265 → HALF_UP → 169.13
        assertThat(new BigDecimal(tree.get("convertedAmount").asText()))
                .isEqualByComparingTo("169.13");
    }

    @Test
    @DisplayName("AC-T-3 end-to-end — Treasury 5xx surfaces as 503 UPSTREAM_UNAVAILABLE")
    void endToEnd5xx() throws Exception {
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(503)));

        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"description":"Coffee","transactionDate":"2026-05-10","amountUsd":"123.45"}""";
        ResponseEntity<String> createResp = rest.exchange(
                "/api/v1/purchases", HttpMethod.POST, new HttpEntity<>(body, json), String.class);
        String id = extractField(createResp.getBody(), "id");

        ResponseEntity<String> convResp = rest.getForEntity(
                "/api/v1/purchases/" + id + "/conversion?currency=CAD", String.class);
        assertThat(convResp.getStatusCode().value()).isEqualTo(503);
        assertThat(convResp.getBody()).contains("UPSTREAM_UNAVAILABLE");
        assertThat(convResp.getHeaders().getFirst("Retry-After")).isEqualTo("300");
    }

    @Test
    @DisplayName("AC-T-3 end-to-end — Treasury sanity-bound breach (rate=0) surfaces as 502 UPSTREAM_BAD_RESPONSE")
    void endToEndSanityBreach() throws Exception {
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    { "data": [
                        { "country_currency_desc": "Canada-Dollar",
                          "record_date": "2026-04-15",
                          "effective_date": "2026-04-15",
                          "exchange_rate": "0" }
                    ] }""")));

        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"description":"Coffee","transactionDate":"2026-05-10","amountUsd":"123.45"}""";
        ResponseEntity<String> createResp = rest.exchange(
                "/api/v1/purchases", HttpMethod.POST, new HttpEntity<>(body, json), String.class);
        String id = extractField(createResp.getBody(), "id");

        ResponseEntity<String> convResp = rest.getForEntity(
                "/api/v1/purchases/" + id + "/conversion?currency=CAD", String.class);
        assertThat(convResp.getStatusCode().value()).isEqualTo(502);
        assertThat(convResp.getBody()).contains("UPSTREAM_BAD_RESPONSE");
        assertThat(convResp.getBody()).contains("rate_sanity");
    }

    private String extractField(String json, String field) throws Exception {
        return objectMapper.readTree(json).get(field).asText();
    }
}
