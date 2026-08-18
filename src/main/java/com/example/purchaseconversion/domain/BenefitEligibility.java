package com.example.purchaseconversion.domain;

import java.util.Objects;

/**
 * A benefit's tier-eligibility rule (eligibility-endpoint-spec.md §3.2 — minimum-tier
 * model): a cardholder is eligible iff their {@link CardTier} meets or exceeds
 * {@code minimumTier}.
 */
public record BenefitEligibility(BenefitId benefitId, CardTier minimumTier) {

    public BenefitEligibility {
        Objects.requireNonNull(benefitId, "benefitId must not be null");
        Objects.requireNonNull(minimumTier, "minimumTier must not be null");
    }
}
