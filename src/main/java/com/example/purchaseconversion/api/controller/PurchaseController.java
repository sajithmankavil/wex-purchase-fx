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
 */
@RestController
@RequestMapping("/api/v1/purchases")
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
    public ResponseEntity<PurchaseResponse> retrieve(@PathVariable("id") String id) {
        Purchase purchase = retrievePurchase.retrieve(parseId(id));
        return ResponseEntity.ok(PurchaseResponse.of(purchase));
    }

    @GetMapping("/{id}/conversion")
    public ResponseEntity<ConversionResponse> convert(
            @PathVariable("id") String id,
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
