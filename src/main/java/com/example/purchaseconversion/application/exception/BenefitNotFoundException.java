package com.example.purchaseconversion.application.exception;

import com.example.purchaseconversion.domain.BenefitId;

import java.util.Objects;

/**
 * Thrown when the given {@code benefitId} does not exist in the eligibility catalog.
 * Maps to HTTP 404 with {@code errorCode = BENEFIT_NOT_FOUND}
 * (eligibility-endpoint-spec.md §2, §3.3).
 *
 * <p>Deliberately distinct from "not eligible" — see spec §3.3 for the rationale
 * (conflating the two would hide catalog bugs behind a false-looking negative result).
 */
public final class BenefitNotFoundException extends DomainException {

    private final BenefitId benefitId;

    public BenefitNotFoundException(BenefitId benefitId) {
        super("benefit not found: " + Objects.requireNonNull(benefitId));
        this.benefitId = benefitId;
    }

    public BenefitId getBenefitId() {
        return benefitId;
    }
}
