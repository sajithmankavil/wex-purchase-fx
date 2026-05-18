package com.example.purchaseconversion.api.controller;

import com.example.purchaseconversion.api.dto.ConversionResponse;
import com.example.purchaseconversion.api.dto.PurchaseRequest;
import com.example.purchaseconversion.api.dto.PurchaseResponse;
import com.example.purchaseconversion.application.exception.MalformedIdentifierException;
import com.example.purchaseconversion.application.port.in.ConvertPurchaseUseCase;
import com.example.purchaseconversion.application.port.in.RegisterPurchaseCommand;
import com.example.purchaseconversion.application.port.in.RegisterPurchaseUseCase;
import com.example.purchaseconversion.application.port.in.RetrievePurchaseUseCase;
import com.example.purchaseconversion.domain.Money;
import com.example.purchaseconversion.domain.Purchase;
import com.example.purchaseconversion.domain.PurchaseId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Objects;

/**
 * HTTP boundary for FR-001 (register), FR-002 (retrieve), FR-003 (convert).
 *
 * <p>Pure delegation: the controller maps DTOs ↔ application commands and
 * domain objects, then defers to the use-case interfaces. No business logic.
 * Validation is Bean Validation on the request DTO (Jakarta); business
 * exceptions are mapped to RFC 9457 responses by
 * {@code ProblemDetailExceptionHandler}.
 *
 * <p>OpenAPI annotations (springdoc) on each method enumerate the RFC 9457
 * error codes the endpoint can produce; the generated OAS is regression-checked
 * by the {@code oasdiff} CI gate against the baseline at
 * {@code infra/openapi/baseline.yaml} (C3 §S2).
 */
@RestController
@RequestMapping("/api/v1/purchases")
@Tag(name = "Purchases", description = "Stored USD purchase transactions and Treasury-driven currency conversion (FR-001..FR-003).")
public class PurchaseController {

    private final RegisterPurchaseUseCase registerPurchase;
    private final RetrievePurchaseUseCase retrievePurchase;
    private final ConvertPurchaseUseCase convertPurchase;

    public PurchaseController(
            RegisterPurchaseUseCase registerPurchase,
            RetrievePurchaseUseCase retrievePurchase,
            ConvertPurchaseUseCase convertPurchase) {
        this.registerPurchase = Objects.requireNonNull(registerPurchase, "registerPurchase must not be null");
        this.retrievePurchase = Objects.requireNonNull(retrievePurchase, "retrievePurchase must not be null");
        this.convertPurchase = Objects.requireNonNull(convertPurchase, "convertPurchase must not be null");
    }

    @PostMapping
    @Operation(
            operationId = "createPurchase",
            summary = "Register a USD purchase",
            description = "FR-001 — stores a purchase with server-generated UUID v7 id, returning 201 Created."
                    + " Bean Validation enforces description ≤ 50 chars + scale-2 amount; ContentGuard rejects"
                    + " PAN-shaped descriptions (AC-010b/c/d/e). Application enforces transactionDate ≤ today.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created",
                    content = @Content(schema = @Schema(implementation = PurchaseResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED | PAN_PATTERN_DETECTED",
                    content = @Content(mediaType = "application/problem+json",
                            examples = {
                                    @ExampleObject(name = "validation-failed",
                                            value = "{\"type\":\"https://wex.example.com/problems/validation-failed\",\"title\":\"Bean validation failed\",\"status\":400,\"errorCode\":\"VALIDATION_FAILED\",\"details\":{\"errors\":[{\"field\":\"description\",\"code\":\"NotBlank\"}]}}"),
                                    @ExampleObject(name = "pan-pattern-detected",
                                            value = "{\"type\":\"https://wex.example.com/problems/pan-pattern-detected\",\"title\":\"PAN-shaped content detected\",\"status\":400,\"errorCode\":\"PAN_PATTERN_DETECTED\",\"details\":{\"reason\":\"luhn\"}}")})),
            @ApiResponse(responseCode = "422", description = "FUTURE_DATE",
                    content = @Content(mediaType = "application/problem+json",
                            examples = @ExampleObject(value = "{\"type\":\"https://wex.example.com/problems/future-date\",\"title\":\"transactionDate is in the future\",\"status\":422,\"errorCode\":\"FUTURE_DATE\",\"details\":{\"transactionDate\":\"2099-01-01\"}}"))),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(mediaType = "application/problem+json"))
    })
    public ResponseEntity<PurchaseResponse> create(@Valid @RequestBody PurchaseRequest request) {
        Purchase saved = registerPurchase.register(new RegisterPurchaseCommand(
                request.description(),
                request.transactionDate(),
                Money.of(request.amountUsd())));
        URI location = UriComponentsBuilder.fromPath("/api/v1/purchases/{id}")
                .buildAndExpand(saved.id().toString())
                .toUri();
        return ResponseEntity.created(location).body(PurchaseResponse.of(saved));
    }

    @GetMapping("/{id}")
    @Operation(
            operationId = "retrievePurchase",
            summary = "Retrieve a stored purchase",
            description = "FR-002 — returns the stored purchase by UUID v7 id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = PurchaseResponse.class))),
            @ApiResponse(responseCode = "400", description = "MALFORMED_IDENTIFIER",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "404", description = "PURCHASE_NOT_FOUND",
                    content = @Content(mediaType = "application/problem+json"))
    })
    public ResponseEntity<PurchaseResponse> retrieve(
            @Parameter(description = "Purchase UUID v7 in canonical 8-4-4-4-12 hex form", required = true)
            @PathVariable("id") String id) {
        Purchase purchase = retrievePurchase.retrieve(parseId(id));
        return ResponseEntity.ok(PurchaseResponse.of(purchase));
    }

    @GetMapping("/{id}/conversion")
    @Operation(
            operationId = "convertPurchase",
            summary = "Convert a stored purchase to a target currency",
            description = "FR-003 — applies the 6-month rate-selection rule (ADR-0001 D-5) and returns a"
                    + " scale-6 exchangeRate (G4-P0-3) + scale-2 convertedAmount (HALF_UP per D-6)."
                    + " currency input is dual-mode: ISO-4217 code (e.g., CAD) or canonical Treasury"
                    + " descriptor (e.g., Canada-Dollar).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ConversionResponse.class),
                            examples = @ExampleObject(name = "ac-014-scale-6", value = "{\n"
                                    + "  \"id\":\"01900000-0000-7000-8000-000000000000\",\n"
                                    + "  \"description\":\"Coffee\",\n"
                                    + "  \"transactionDate\":\"2026-05-10\",\n"
                                    + "  \"amountUsd\":\"123.45\",\n"
                                    + "  \"targetCurrency\":\"Canada-Dollar\",\n"
                                    + "  \"exchangeRate\":\"1.370000\",\n"
                                    + "  \"exchangeRateDate\":\"2026-04-15\",\n"
                                    + "  \"convertedAmount\":\"169.13\"\n"
                                    + "}"))),
            @ApiResponse(responseCode = "400", description = "MALFORMED_IDENTIFIER | INVALID_CURRENCY",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "422", description = "CONVERSION_RATE_NOT_AVAILABLE",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "502", description = "UPSTREAM_BAD_RESPONSE",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "503", description = "UPSTREAM_UNAVAILABLE",
                    content = @Content(mediaType = "application/problem+json"))
    })
    public ResponseEntity<ConversionResponse> convert(
            @Parameter(description = "Purchase UUID v7", required = true)
            @PathVariable("id") String id,
            @Parameter(description = "ISO-4217 code or canonical Treasury descriptor; case-insensitive",
                    required = true, example = "CAD")
            @RequestParam("currency") String currency) {
        return ResponseEntity.ok(ConversionResponse.of(
                convertPurchase.convert(parseId(id), currency)));
    }

    private static PurchaseId parseId(String raw) {
        try {
            return PurchaseId.fromString(raw);
        } catch (RuntimeException e) {
            throw new MalformedIdentifierException(raw, e);
        }
    }
}
