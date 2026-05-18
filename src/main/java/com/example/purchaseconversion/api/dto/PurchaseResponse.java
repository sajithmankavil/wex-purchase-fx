package com.example.purchaseconversion.api.dto;

import com.example.purchaseconversion.domain.Purchase;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Outbound payload for POST/GET on a purchase (FR-001 + FR-002 happy path).
 *
 * <p>Mirrors the request shape plus the server-assigned UUID v7 id. Per
 * api-contracts.md §1, decimal amounts are serialised as JSON strings with
 * exact scale preservation; {@code amountUsd} is always scale 2 (cents).
 */
@Schema(name = "PurchaseResponse", description = "Outbound representation of a stored purchase.")
public record PurchaseResponse(
        @Schema(description = "Server-assigned UUID v7 (ADR-0001 D-7); canonical 8-4-4-4-12 hex form.",
                example = "01900000-0000-7000-8000-000000000000")
        String id,
        @Schema(description = "Stored description verbatim (ContentGuard-validated at create time).",
                example = "Coffee")
        String description,
        @Schema(description = "Purchase date (UTC).", example = "2026-05-10", format = "date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate transactionDate,
        // Shape.STRING preserves the BigDecimal's scale (trailing zeros) in JSON.
        // Without it, Jackson outputs canonical form (4.50 -> 4.5), violating
        // api-contracts.md §1's "exact scale preservation" requirement and the
        // brief's "rounded to the nearest cent" expectation that the API always
        // shows scale 2.
        @Schema(description = "USD amount, scale 2.", example = "123.45", type = "string")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal amountUsd) {

    public static PurchaseResponse of(Purchase p) {
        return new PurchaseResponse(
                p.id().toString(),
                p.description(),
                p.transactionDate(),
                p.amountUsd().value());
    }
}
