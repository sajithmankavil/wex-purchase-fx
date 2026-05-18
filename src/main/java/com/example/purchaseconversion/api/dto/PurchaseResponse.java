package com.example.purchaseconversion.api.dto;

import com.example.purchaseconversion.domain.Purchase;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Outbound payload for POST/GET on a purchase (FR-001 + FR-002 happy path).
 *
 * <p>Mirrors the request shape plus the server-assigned UUID v7 id. Per
 * api-contracts.md §1, decimal amounts are serialised as JSON strings with
 * exact scale preservation; {@code amountUsd} is always scale 2 (cents).
 */
public record PurchaseResponse(
        String id,
        String description,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate transactionDate,
        BigDecimal amountUsd) {

    public static PurchaseResponse of(Purchase p) {
        return new PurchaseResponse(
                p.id().toString(),
                p.description(),
                p.transactionDate(),
                p.amountUsd().value());
    }
}
