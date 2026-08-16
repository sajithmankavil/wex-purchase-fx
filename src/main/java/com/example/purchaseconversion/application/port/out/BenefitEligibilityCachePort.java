package com.example.purchaseconversion.application.port.out;

import com.example.purchaseconversion.domain.BenefitId;
import com.example.purchaseconversion.domain.CardTier;

import java.util.Optional;

/**
 * Outbound port for the in-memory benefit-eligibility reference-data cache
 * (eligibility-endpoint-spec.md §4.1).
 *
 * <p>Implemented by {@code BenefitEligibilityCacheAdapter} in
 * {@code infrastructure.cache}. Per the spec's latency design, this is a synchronous
 * in-memory read with no per-request I/O — the cache is populated and periodically
 * refreshed by the adapter itself, not by the application layer.
 */
public interface BenefitEligibilityCachePort {

    /**
     * Returns the benefit's minimum required tier, or {@link Optional#empty()} if the
     * benefit is not known to the catalog (spec §3.3 — maps to 404 at the API layer).
     */
    Optional<CardTier> minimumTierFor(BenefitId benefitId);
}
