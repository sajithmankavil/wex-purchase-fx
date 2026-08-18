package com.example.purchaseconversion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Outbound payload for the benefit-eligibility check (eligibility-endpoint-spec.md §2).
 */
@Schema(name = "EligibilityResponse", description = "Whether a cardholder tier is eligible for a benefit.")
public record EligibilityResponse(
        @Schema(description = "Echoed benefit identifier.", example = "BEN-1042")
        String benefitId,
        @Schema(description = "Echoed cardholder tier.", example = "SIGNATURE")
        String tier,
        @Schema(description = "True iff the tier meets or exceeds the benefit's minimum tier.")
        boolean eligible) {
}
