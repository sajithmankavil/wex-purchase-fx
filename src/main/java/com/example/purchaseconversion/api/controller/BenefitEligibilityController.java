package com.example.purchaseconversion.api.controller;

import com.example.purchaseconversion.api.dto.EligibilityResponse;
import com.example.purchaseconversion.api.interceptor.EligibilityAuditInterceptor;
import com.example.purchaseconversion.application.eligibility.EligibilityResult;
import com.example.purchaseconversion.application.exception.BenefitNotFoundException;
import com.example.purchaseconversion.application.exception.InvalidTierException;
import com.example.purchaseconversion.application.port.in.CheckEligibilityUseCase;
import com.example.purchaseconversion.domain.BenefitId;
import com.example.purchaseconversion.domain.CardTier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
 * validates the request, defers to the use-case interface, and maps the exhaustive
 * {@link EligibilityResult} outcome to a response. No business logic and no
 * authentication here — auth is an explicit pre-production gap for this drill
 * (spec §5 scope-affecting #3).
 *
 * <p>Audit logging is deliberately NOT done here — see
 * {@link EligibilityAuditInterceptor}, which fires after the response has been
 * sent. This method's only audit-related job is stamping the request attributes
 * the interceptor reads.
 */
@RestController
@RequestMapping("/api/v1/benefits")
@Tag(name = "Benefit Eligibility", description = "Cardholder tier vs. benefit minimum-tier check.")
public class BenefitEligibilityController {

    private final CheckEligibilityUseCase checkEligibility;

    public BenefitEligibilityController(CheckEligibilityUseCase checkEligibility) {
        this.checkEligibility = Objects.requireNonNull(checkEligibility, "checkEligibility must not be null");
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
            HttpServletRequest request,
            @Parameter(description = "Opaque benefit-catalog identifier", required = true, example = "BEN-1042")
            @PathVariable("benefitId") String benefitIdRaw,
            @Parameter(description = "Cardholder tier — PLATINUM, SIGNATURE, or INFINITE; case-sensitive",
                    required = true, example = "SIGNATURE")
            @RequestParam("tier") String tierRaw) {
        BenefitId benefitId = BenefitId.of(benefitIdRaw);
        CardTier tier = CardTier.parse(tierRaw).orElseThrow(() -> new InvalidTierException(tierRaw));

        EligibilityResult result = checkEligibility.check(benefitId, tier);

        return switch (result) {
            case EligibilityResult.Eligible eligible ->
                    respond(request, eligible.benefitId(), eligible.tier(), true, null);
            case EligibilityResult.NotEligible notEligible ->
                    respond(request, notEligible.benefitId(), notEligible.tier(), false,
                            notEligible.minimumTier());
            case EligibilityResult.NotFound notFound ->
                    throw new BenefitNotFoundException(notFound.benefitId());
        };
    }

    private ResponseEntity<EligibilityResponse> respond(
            HttpServletRequest request, BenefitId benefitId, CardTier tier, boolean eligible,
            CardTier minimumTier) {
        // Stamped for EligibilityAuditInterceptor#afterCompletion — never read within
        // this request; the interceptor consumes these after the response is sent.
        // minimumTier is null for Eligible (no gap to report) — the interceptor treats
        // that as "attribute not stamped" and omits it from the audit line accordingly.
        request.setAttribute(EligibilityAuditInterceptor.ATTR_BENEFIT_ID, benefitId.value());
        request.setAttribute(EligibilityAuditInterceptor.ATTR_TIER, tier.name());
        request.setAttribute(EligibilityAuditInterceptor.ATTR_ELIGIBLE, eligible);
        if (minimumTier != null) {
            request.setAttribute(EligibilityAuditInterceptor.ATTR_MINIMUM_TIER, minimumTier.name());
        }
        return ResponseEntity.ok(new EligibilityResponse(benefitId.value(), tier.name(), eligible));
    }
}
