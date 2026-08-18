package com.example.purchaseconversion.application.port.in;

import com.example.purchaseconversion.application.eligibility.EligibilityResult;
import com.example.purchaseconversion.domain.BenefitId;
import com.example.purchaseconversion.domain.CardTier;

/**
 * Inbound port for the cardholder benefit-eligibility check
 * (eligibility-endpoint-spec.md §1, §2).
 */
public interface CheckEligibilityUseCase {

    /**
     * Returns the eligibility outcome for a cardholder of {@code tier} against
     * {@code benefitId} — exactly one of {@link EligibilityResult}'s three variants.
     */
    EligibilityResult check(BenefitId benefitId, CardTier tier);
}
