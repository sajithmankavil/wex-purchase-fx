package com.example.purchaseconversion.application.eligibility;

import com.example.purchaseconversion.domain.BenefitId;
import com.example.purchaseconversion.domain.CardTier;

/**
 * Outcome of {@link EligibilityService#check}, exhaustively one of three shapes
 * (eligibility-endpoint-spec.md §2, §3.3).
 *
 * <p>Deliberately a sealed result type rather than "boolean + exception for the
 * not-found case" — {@code NotFound} is a routine, expected outcome (spec §3.3
 * treats it as distinct from "not eligible", not as an error), and callers are
 * forced by exhaustive {@code switch} to handle all three cases rather than
 * discovering the not-found path only via a caught exception.
 */
public sealed interface EligibilityResult {

    /** Cardholder tier meets or exceeds the benefit's minimum tier. */
    record Eligible(BenefitId benefitId, CardTier tier) implements EligibilityResult {
    }

    /** Cardholder tier is below the benefit's minimum tier. */
    record NotEligible(BenefitId benefitId, CardTier tier, CardTier minimumTier) implements EligibilityResult {
    }

    /** {@code benefitId} does not exist in the eligibility catalog (spec §3.3). */
    record NotFound(BenefitId benefitId) implements EligibilityResult {
    }
}
