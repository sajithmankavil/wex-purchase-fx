package com.example.purchaseconversion.infrastructure.cache;

import com.example.purchaseconversion.application.port.out.BenefitEligibilityCachePort;
import com.example.purchaseconversion.domain.BenefitEligibility;
import com.example.purchaseconversion.domain.BenefitId;
import com.example.purchaseconversion.domain.CardTier;
import com.example.purchaseconversion.infrastructure.persistence.BenefitEligibilityRepoAdapter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * In-memory reference-data cache backing {@link BenefitEligibilityCachePort}
 * (eligibility-endpoint-spec.md §4.1, §4.3).
 *
 * <p>Loads the full {@code benefit_eligibility} table into memory on startup and
 * refreshes it on a fixed schedule; every request is served from the in-memory
 * snapshot with zero per-request I/O, which is what makes the p99 &lt; 100ms
 * target achievable (spec §4.1's resolution of the "no caching layer" tension).
 *
 * <h2>Concurrency</h2>
 *
 * <p>The snapshot is held in an {@link AtomicReference}; a refresh replaces it with
 * a single {@code set(...)} call. Readers always see either the fully-old or the
 * fully-new map, never a partially-built one — there is no in-place mutation of a
 * shared map, so no torn reads are possible during a swap (spec §7 concurrent-refresh
 * test).
 *
 * <h2>Startup / failure behaviour</h2>
 *
 * <p>The snapshot is {@code null} until the first successful load. While {@code null},
 * {@link #minimumTierFor} returns empty for everything (fail closed — never serve
 * from an empty or partial map as if it were authoritative) and
 * {@code BenefitEligibilityHealthIndicator} reports the service not-ready. A DB
 * failure during a later scheduled refresh is logged at WARN and the previous
 * snapshot is kept in place — live requests are never failed because a background
 * refresh failed (spec §4.3).
 */
@Component
public class BenefitEligibilityCacheAdapter implements BenefitEligibilityCachePort {

    private static final Logger LOG = LoggerFactory.getLogger(BenefitEligibilityCacheAdapter.class);

    private final BenefitEligibilityRepoAdapter repository;
    private final AtomicReference<Map<BenefitId, CardTier>> snapshot = new AtomicReference<>();

    public BenefitEligibilityCacheAdapter(BenefitEligibilityRepoAdapter repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Optional<CardTier> minimumTierFor(BenefitId benefitId) {
        Objects.requireNonNull(benefitId, "benefitId must not be null");
        Map<BenefitId, CardTier> current = snapshot.get();
        if (current == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(current.get(benefitId));
    }

    /** True once the first load has succeeded — the readiness signal for this feature. */
    public boolean isLoaded() {
        return snapshot.get() != null;
    }

    @PostConstruct
    void initialLoad() {
        try {
            loadOnce();
            LOG.info("benefit_eligibility.cache.initial_load.success count={}", snapshot.get().size());
        } catch (RuntimeException e) {
            // Deliberately does not rethrow: a transient DB blip at startup should not
            // crash-loop the whole service. The health indicator keeps readiness DOWN
            // until a load (this one or a later scheduled retry) succeeds.
            LOG.warn("benefit_eligibility.cache.initial_load.failure errClass={}",
                    e.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelayString = "${wex.eligibility.refresh-interval-ms:300000}")
    void scheduledRefresh() {
        try {
            int before = isLoaded() ? snapshot.get().size() : -1;
            loadOnce();
            LOG.debug("benefit_eligibility.cache.refresh.success previousCount={} count={}",
                    before, snapshot.get().size());
        } catch (RuntimeException e) {
            LOG.warn("benefit_eligibility.cache.refresh.failure errClass={} keepingLastGoodSnapshot={}",
                    e.getClass().getSimpleName(), isLoaded());
        }
    }

    private void loadOnce() {
        List<BenefitEligibility> rows = repository.findAll();
        Map<BenefitId, CardTier> fresh = rows.stream()
                .collect(Collectors.toUnmodifiableMap(
                        BenefitEligibility::benefitId, BenefitEligibility::minimumTier));
        snapshot.set(fresh);
    }
}
