package com.example.purchaseconversion.application.eligibility;

import com.example.purchaseconversion.application.exception.BenefitNotFoundException;
import com.example.purchaseconversion.application.port.in.CheckEligibilityUseCase;
import com.example.purchaseconversion.application.port.out.BenefitEligibilityCachePort;
import com.example.purchaseconversion.domain.BenefitId;
import com.example.purchaseconversion.domain.CardTier;
import com.example.purchaseconversion.domain.EligibilityPolicy;

import java.util.Objects;

/**
 * Application service for the cardholder benefit-eligibility check
 * (eligibility-endpoint-spec.md §1).
 *
 * <p>Pure POJO — no Spring annotations, no I/O. Wiring is the responsibility of
 * {@code config} (matches this codebase's existing application-layer convention,
 * e.g. {@code PurchaseService}). {@code tier} arrives already validated — parsing
 * the raw request string into a {@link CardTier} is a request-validation concern
 * handled at the API layer (spec §3.3), not here.
 */
public final class EligibilityService implements CheckEligibilityUseCase {

    private final BenefitEligibilityCachePort cache;

    public EligibilityService(BenefitEligibilityCachePort cache) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
    }

    @Override
    public boolean check(BenefitId benefitId, CardTier tier) {
        Objects.requireNonNull(benefitId, "benefitId must not be null");
        Objects.requireNonNull(tier, "tier must not be null");
        CardTier minimumTier = cache.minimumTierFor(benefitId)
                .orElseThrow(() -> new BenefitNotFoundException(benefitId));
        return EligibilityPolicy.isEligible(tier, minimumTier);
    }
}
