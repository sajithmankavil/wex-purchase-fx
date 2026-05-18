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
        @Schema(description = "USD amount, scale 2.", example = "123.45", type = "string")
        BigDecimal amountUsd) {

    public static PurchaseResponse of(Purchase p) {
        return new PurchaseResponse(
                p.id().toString(),
                p.description(),
                p.transactionDate(),
                p.amountUsd().value());
    }
}
