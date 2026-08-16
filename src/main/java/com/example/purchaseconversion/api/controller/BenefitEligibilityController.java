package com.example.purchaseconversion.api.controller;

import com.example.purchaseconversion.api.dto.EligibilityResponse;
import com.example.purchaseconversion.application.exception.InvalidTierException;
import com.example.purchaseconversion.application.port.in.CheckEligibilityUseCase;
import com.example.purchaseconversion.domain.BenefitId;
import com.example.purchaseconversion.domain.CardTier;
import com.example.purchaseconversion.observability.EligibilityAuditLogger;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * HTTP boundary for the cardholder benefit-eligibility check
 * (eligibility-endpoint-spec.md §2).
 *
 * <p>Pure delegation, matching {@code PurchaseController}'s convention: parses and
 * validates the request, defers to the use-case interface, and logs one audit line
 * per call. No business logic and no authentication here — auth is an explicit
 * pre-production gap for this drill (spec §5 scope-affecting #3).
 */
@RestController
@RequestMapping("/api/v1/benefits")
@Tag(name = "Benefit Eligibility", description = "Cardholder tier vs. benefit minimum-tier check.")
public class BenefitEligibilityController {

    private final CheckEligibilityUseCase checkEligibility;
    private final EligibilityAuditLogger auditLogger;

    public BenefitEligibilityController(
            CheckEligibilityUseCase checkEligibility, EligibilityAuditLogger auditLogger) {
        this.checkEligibility = Objects.requireNonNull(checkEligibility, "checkEligibility must not be null");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger must not be null");
    }

    @GetMapping("/{benefitId}/eligibility")
    @Operation(
            operationId = "checkBenefitEligibility",
            summary = "Check whether a cardholder tier is eligible for a benefit",
            description = "Minimum-tier model (spec §3.2): eligible iff tier >= the benefit's minimum tier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = EligibilityResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_TIER | MISSING_TIER",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "404", description = "BENEFIT_NOT_FOUND",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(mediaType = "application/problem+json"))
    })
    public ResponseEntity<EligibilityResponse> checkEligibility(
            @Parameter(description = "Opaque benefit-catalog identifier", required = true, example = "BEN-1042")
            @PathVariable("benefitId") String benefitIdRaw,
            @Parameter(description = "Cardholder tier — PLATINUM, SIGNATURE, or INFINITE; case-sensitive",
                    required = true, example = "SIGNATURE")
            @RequestParam("tier") String tierRaw) {
        long startNanos = System.nanoTime();

        BenefitId benefitId = BenefitId.of(benefitIdRaw);
        CardTier tier = CardTier.parse(tierRaw).orElseThrow(() -> new InvalidTierException(tierRaw));

        boolean eligible = checkEligibility.check(benefitId, tier);

        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        auditLogger.logCheck(benefitId.value(), tier.name(), eligible, latencyMs);

        return ResponseEntity.ok(new EligibilityResponse(benefitId.value(), tier.name(), eligible));
    }
}
