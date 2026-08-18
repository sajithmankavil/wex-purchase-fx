package com.example.purchaseconversion.infrastructure.health;

import com.example.purchaseconversion.infrastructure.cache.BenefitEligibilityCacheAdapter;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Readiness signal for the benefit-eligibility cache (eligibility-endpoint-spec.md §4.3).
 *
 * <p>Reports DOWN until the cache's first load succeeds — the service must not
 * serve eligibility checks against an empty or not-yet-loaded map, since either a
 * false "not eligible" or a false "eligible" (depending on how an empty map would
 * be interpreted) is worse than briefly failing readiness (spec §4.3's fail-closed
 * cold-start decision).
 */
@Component
public class BenefitEligibilityHealthIndicator implements HealthIndicator {

    private final BenefitEligibilityCacheAdapter cache;

    public BenefitEligibilityHealthIndicator(BenefitEligibilityCacheAdapter cache) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
    }

    @Override
    public Health health() {
        if (!cache.isLoaded()) {
            return Health.down()
                    .withDetail("reason", "benefit_eligibility_cache_not_loaded")
                    .build();
        }
        return Health.up().build();
    }
}
