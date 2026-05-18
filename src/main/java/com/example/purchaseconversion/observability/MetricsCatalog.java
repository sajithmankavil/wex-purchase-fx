package com.example.purchaseconversion.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Centralised metric-name catalogue per C2 `00-prompt.md` §S1 + observability.md.
 *
 * <p>Names are constants (compile-time checked); tags are passed as {@link Tags}.
 * The catalogue carries no state of its own — Micrometer's {@link MeterRegistry}
 * owns counter/gauge identity by name + tag set.
 *
 * <p>{@code http.server.requests} is provided automatically by Spring Boot's
 * actuator + WebMVC instrumentation; not duplicated here. The catalogue only
 * names application-specific families.
 */
@Component
public class MetricsCatalog {

    /** Validation failure on purchase create — A-021 PAN-guard false-positive source. */
    public static final String PURCHASE_VALIDATION_ERROR = "purchase.create.validation_error";

    /** Treasury client request count per outcome label (matches TreasuryClientAdapter audit outcomes). */
    public static final String TREASURY_REQUESTS = "treasury.client.requests";

    /** Single-flight gate dedup ratio (winners / total entries). */
    public static final String SINGLE_FLIGHT_DEDUP = "exchange_rate.single_flight.dedup_ratio";

    /** Hot-cache hit ratio (hits / lookups). */
    public static final String HOT_CACHE_HIT_RATIO = "exchange_rate.hot_cache.hit_ratio";

    /** Single-flight loser outcome counter ({@code db_hit | mirrored_failure | timeout | shutdown}). */
    public static final String SINGLE_FLIGHT_LOSER_OUTCOME = "single_flight.loser_outcome";

    /** Currency-alias drift detected — emitted when {@link com.example.purchaseconversion.application.exception.InvalidCurrencyException} is thrown. */
    public static final String CURRENCY_ALIAS_DRIFT_DETECTED = "currency_alias.drift.detected";

    /** Content-guard invocations — used by RateLimitOrderingIT to assert G8-P0-1 ordering. */
    public static final String CONTENT_GUARD_INVOCATIONS = "contentguard.invocations";

    private final MeterRegistry registry;

    public MetricsCatalog(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /** Increment the validation-error counter with the given {@code reason} label. */
    public void purchaseValidationError(String reason) {
        Counter.builder(PURCHASE_VALIDATION_ERROR)
                .tags(Tags.of("reason", reason))
                .register(registry)
                .increment();
    }

    /** Increment the Treasury request counter with the given outcome label. */
    public void treasuryRequest(String outcome) {
        Counter.builder(TREASURY_REQUESTS)
                .tags(Tags.of("outcome", outcome))
                .register(registry)
                .increment();
    }

    /** Increment the single-flight loser-outcome counter with the given type. */
    public void singleFlightLoserOutcome(String type) {
        Counter.builder(SINGLE_FLIGHT_LOSER_OUTCOME)
                .tags(Tags.of("type", type))
                .register(registry)
                .increment();
    }

    /** Increment the alias-drift counter — caller passes the hashed currency only. */
    public void aliasDriftDetected() {
        Counter.builder(CURRENCY_ALIAS_DRIFT_DETECTED)
                .register(registry)
                .increment();
    }

    /** Increment the ContentGuard invocation counter — bound at every rejection + accept. */
    public void contentGuardInvocation() {
        Counter.builder(CONTENT_GUARD_INVOCATIONS)
                .register(registry)
                .increment();
    }

    /** Visible for tests — exposes registry for direct meter inspection. */
    public MeterRegistry registry() {
        return registry;
    }
}
