package com.example.purchaseconversion.application.port.in;

import com.example.purchaseconversion.domain.BenefitId;
import com.example.purchaseconversion.domain.CardTier;

/**
 * Inbound port for the cardholder benefit-eligibility check
 * (eligibility-endpoint-spec.md §1, §2).
 */
public interface CheckEligibilityUseCase {

    /**
     * Returns whether a cardholder of {@code tier} is eligible for {@code benefitId}.
     *
     * @throws com.example.purchaseconversion.application.exception.BenefitNotFoundException
     *         if {@code benefitId} is not known to the catalog (spec §3.3 — maps to 404).
     */
    boolean check(BenefitId benefitId, CardTier tier);
}
