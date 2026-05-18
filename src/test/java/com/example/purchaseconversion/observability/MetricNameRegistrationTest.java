package com.example.purchaseconversion.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the C2 prompt §S1 metric-name catalogue: the registered names match the
 * prompt's enumeration. Names are the public contract — dashboards, alerts, and
 * tail-sampling rules subscribe to them. The unit test pins them so a rename
 * surfaces at PR time.
 */
class MetricNameRegistrationTest {

    private MeterRegistry registry;
    private MetricsCatalog catalog;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        catalog = new MetricsCatalog(registry);
    }

    @Test
    @DisplayName("validation error counter is registered with the prompt-defined name + tag shape")
    void validationErrorRegistered() {
        catalog.purchaseValidationError("pan_pattern");
        catalog.purchaseValidationError("future_date");

        assertThat(registry.find("purchase.create.validation_error")
                .tag("reason", "pan_pattern")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.find("purchase.create.validation_error")
                .tag("reason", "future_date")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("treasury.client.requests carries outcome tag matching the audit-log outcome labels")
    void treasuryRequestsByOutcome() {
        catalog.treasuryRequest("success");
        catalog.treasuryRequest("circuit_open");
        catalog.treasuryRequest("http_5xx:503");

        assertThat(registry.find("treasury.client.requests")
                .tag("outcome", "success")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.find("treasury.client.requests")
                .tag("outcome", "circuit_open")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("single_flight.loser_outcome carries type tag")
    void singleFlightLoserOutcome() {
        catalog.singleFlightLoserOutcome("db_hit");
        catalog.singleFlightLoserOutcome("mirrored_failure");
        catalog.singleFlightLoserOutcome("timeout");
        catalog.singleFlightLoserOutcome("shutdown");

        assertThat(registry.find("single_flight.loser_outcome").meters()).hasSize(4);
    }

    @Test
    @DisplayName("currency_alias.drift.detected is registered (untagged counter)")
    void aliasDrift() {
        catalog.aliasDriftDetected();
        assertThat(registry.find("currency_alias.drift.detected").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("contentguard.invocations is registered (G8-P0-1 ordering test source)")
    void contentGuardInvocations() {
        catalog.contentGuardInvocation();
        catalog.contentGuardInvocation();
        assertThat(registry.find("contentguard.invocations").counter().count())
                .isEqualTo(2.0);
    }

    @Test
    @DisplayName("all metric-name constants conform to dotted lower-snake convention")
    void nameConvention() {
        assertNameConvention(MetricsCatalog.PURCHASE_VALIDATION_ERROR);
        assertNameConvention(MetricsCatalog.TREASURY_REQUESTS);
        assertNameConvention(MetricsCatalog.SINGLE_FLIGHT_DEDUP);
        assertNameConvention(MetricsCatalog.HOT_CACHE_HIT_RATIO);
        assertNameConvention(MetricsCatalog.SINGLE_FLIGHT_LOSER_OUTCOME);
        assertNameConvention(MetricsCatalog.CURRENCY_ALIAS_DRIFT_DETECTED);
        assertNameConvention(MetricsCatalog.CONTENT_GUARD_INVOCATIONS);
    }

    private static void assertNameConvention(String name) {
        assertThat(name).matches("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
                .as("metric name '%s' must be dotted lower-snake (a.b.c)", name);
    }
}
