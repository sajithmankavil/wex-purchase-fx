package com.example.purchaseconversion.infrastructure.treasury;

import com.example.purchaseconversion.application.exception.UpstreamBadResponseException;
import com.example.purchaseconversion.application.exception.UpstreamUnavailableException;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC-T-3 widened — 12 case Treasury contract suite per chunks/13-B2-treasury-singleflight/00-prompt.md.
 *
 * <p>WireMock backs the Treasury Fiscal Data API. Each test stubs a specific response
 * shape and asserts the adapter maps it to either a successful parse or one of the
 * two domain exceptions ({@link UpstreamBadResponseException} /
 * {@link UpstreamUnavailableException}).
 *
 * <p>The adapter is wired through the full Spring context (Resilience4j, single-flight,
 * repository) because the fallback path + DB upsert behaviour are part of the contract.
 * Postgres backs the test via {@link AbstractPostgresIT}.
 */
@SpringBootTest
@TestPropertySource(properties = {
        // Loosen CB so individual test stubs don't accidentally open the breaker.
        "resilience4j.circuitbreaker.instances.treasuryClient.sliding-window-size=100",
        "resilience4j.circuitbreaker.instances.treasuryClient.minimum-number-of-calls=20",
        "resilience4j.circuitbreaker.instances.treasuryClient.wait-duration-in-open-state=100",
        // Disable retry inflation for individual fail cases — predictable single-attempt outcomes.
        "resilience4j.retry.instances.treasuryClient.max-attempts=1"
})
class TreasuryClientIT extends AbstractPostgresIT {

    private static final CurrencyDescriptor CAD = CurrencyDescriptor.of("Canada-Dollar");
    private static final LocalDate WINDOW_LOWER = LocalDate.of(2026, 1, 1);
    private static final LocalDate WINDOW_UPPER = LocalDate.of(2026, 5, 17);

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

    @Nested
    @DisplayName("Happy + empty + scale variants (AC-T-3 cases 1, 2, 8, 9, 10)")
    class HappyAndScale {

        @Test
        @DisplayName("AC-T-3 case 1 — happy path; parsed rates persisted scale-6")
        void happyPath() {
            stubOk(rows("\"1.370\""));
            List<ExchangeRate> fetched = treasuryClient.fetchRates(CAD, WINDOW_LOWER, WINDOW_UPPER);

            assertThat(fetched).hasSize(1);
            ExchangeRate r = fetched.get(0);
            assertThat(r.currency()).isEqualTo(CAD);
            assertThat(r.rate().scale()).isEqualTo(6);
            assertThat(r.rate()).isEqualByComparingTo("1.370000");

            List<ExchangeRate> persisted = exchangeRateRepository.findInWindow(CAD, WINDOW_LOWER, WINDOW_UPPER);
            assertThat(persisted).hasSize(1);
        }

        @Test
        @DisplayName("AC-T-3 case 2 — empty result returns empty list (no exception)")
        void emptyResult() {
            stubOk("{\"data\":[]}");
            List<ExchangeRate> fetched = treasuryClient.fetchRates(CAD, WINDOW_LOWER, WINDOW_UPPER);
            assertThat(fetched).isEmpty();
        }

        @Test
        @DisplayName("AC-T-3 case 8 — trailing-zero scale (148.0 → 148.000000)")
        void trailingZeroScale() {
            stubOk(rows("\"148.0\""));
            List<ExchangeRate> fetched = treasuryClient.fetchRates(CAD, WINDOW_LOWER, WINDOW_UPPER);
            assertThat(fetched.get(0).rate()).isEqualByComparingTo("148.000000");
            assertThat(fetched.get(0).rate().scale()).isEqualTo(6);
        }

        @Test
        @DisplayName("AC-T-3 case 9 — leading-zero scale (0.085 → 0.085000)")
        void leadingZeroScale() {
            stubOk(rows("\"0.085\""));
            List<ExchangeRate> fetched = treasuryClient.fetchRates(CAD, WINDOW_LOWER, WINDOW_UPPER);
            assertThat(fetched.get(0).rate()).isEqualByComparingTo("0.085000");
        }

        @Test
        @DisplayName("AC-T-3 case 10 — high-precision rate rounds HALF_UP at scale 6")
        void highPrecisionRate() {
            stubOk(rows("\"1.1234567\""));
            List<ExchangeRate> fetched = treasuryClient.fetchRates(CAD, WINDOW_LOWER, WINDOW_UPPER);
            assertThat(fetched.get(0).rate()).isEqualByComparingTo("1.123457");
        }

        @Test
        @DisplayName("AC-T-3 case 12 — sanity-ceiling boundary (1e30) is accepted")
        void sanityCeilingBoundary() {
            stubOk(rows("\"1e30\""));
            List<ExchangeRate> fetched = treasuryClient.fetchRates(CAD, WINDOW_LOWER, WINDOW_UPPER);
            assertThat(fetched.get(0).rate()).isEqualByComparingTo(new BigDecimal("1e30"));
        }
    }

    @Nested
    @DisplayName("Schema + sanity rejections (AC-T-3 cases 3, 5, 6, 7, 11; AC-024b)")
    class SchemaAndSanity {

        @Test
        @DisplayName("AC-T-3 case 3 — malformed JSON envelope → UpstreamBadResponse")
        void malformedJson() {
            wireMock.stubFor(any(anyUrl()).willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{not-json")));
            assertThatThrownBy(() -> treasuryClient.fetchRates(CAD, WINDOW_LOWER, WINDOW_UPPER))
                    .isInstanceOf(UpstreamBadResponseException.class);
        }

        @Test
        @DisplayName("AC-T-3 case 5 — null exchange_rate field → UpstreamBadResponse")
        void nullRate() {
            String body = """
                    { "data": [
                        { "country_currency_desc": "Canada-Dollar",
                          "record_date": "2026-04-15",
                          "effective_date": "2026-04-15",
                          "exchange_rate": null }
                    ] }""";
            stubOk(body);
            assertThatThrownBy(() -> treasuryClient.fetchRates(CAD, WINDOW_LOWER, WINDOW_UPPER))
                    .isInstanceOf(UpstreamBadResponseException.class)
                    .satisfies(t -> assertThat(((UpstreamBadResponseException) t).getReason())
                            .contains("missing_exchange_rate"));
        }

        @Test
        @DisplayName("AC-T-3 case 6 — zero exchange_rate rejected (AC-024b)")
        void zeroRate() {
            stubOk(rows("\"0\""));
            assertThatThrownBy(() -> treasuryClient.fetchRates(CAD, WINDOW_LOWER, WINDOW_UPPER))
                    .isInstanceOf(UpstreamBadResponseException.class)
                    .satisfies(t -> assertThat(((UpstreamBadResponseException) t).getReason())
                            .contains("rate_sanity"));
        }

        @Test
        @DisplayName("AC-T-3 case 7 — negative exchange_rate rejected")
        void negativeRate() {
            stubOk(rows("\"-1.0\""));
            assertThatThrownBy(() -> treasuryClient.fetchRates(CAD, WINDOW_LOWER, WINDOW_UPPER))
                    .isInstanceOf(UpstreamBadResponseException.class);
        }

        @Test
        @DisplayName("AC-T-3 case 11 — exchange_rate above 1e30 ceiling rejected")
        void aboveSanityCeiling() {
            stubOk(rows("\"1.1e30\""));
            assertThatThrownBy(() -> treasuryClient.fetchRates(CAD, WINDOW_LOWER, WINDOW_UPPER))
                    .isInstanceOf(UpstreamBadResponseException.class);
        }
    }

    @Nested
    @DisplayName("Upstream availability failures (AC-T-3 cases 4 + AC-023)")
    class Availability {

        @Test
        @DisplayName("AC-T-3 case 4 — 5xx maps to UpstreamUnavailable")
        void fiveHundred() {
            wireMock.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(503)));
            assertThatThrownBy(() -> treasuryClient.fetchRates(CAD, WINDOW_LOWER, WINDOW_UPPER))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        @DisplayName("4xx maps to UpstreamBadResponse")
        void fourHundred() {
            wireMock.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(400)));
            assertThatThrownBy(() -> treasuryClient.fetchRates(CAD, WINDOW_LOWER, WINDOW_UPPER))
                    .isInstanceOf(UpstreamBadResponseException.class)
                    .satisfies(t -> assertThat(((UpstreamBadResponseException) t).getReason())
                            .contains("http_4xx"));
        }
    }

    private void stubOk(String body) {
        wireMock.stubFor(any(anyUrl()).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    private String rows(String rateJsonValue) {
        return """
                { "data": [
                    { "country_currency_desc": "Canada-Dollar",
                      "record_date": "2026-04-15",
                      "effective_date": "2026-04-15",
                      "exchange_rate": %s }
                ] }""".formatted(rateJsonValue);
    }
}
