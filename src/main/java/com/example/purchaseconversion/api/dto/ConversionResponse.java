package com.example.purchaseconversion.api.dto;

import com.example.purchaseconversion.application.conversion.ConversionResult;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Outbound payload for GET /api/v1/purchases/{id}/conversion (FR-003).
 *
 * <p>Per api-contracts.md §1 the {@code exchangeRate} is normalised to scale 6
 * on the wire (G4-P0-3 — e.g., Treasury's {@code "148.0"} surfaces as
 * {@code "148.000000"}). {@code convertedAmount} is scale 2 (cents, HALF_UP).
 */
public record ConversionResponse(
        String id,
        String description,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate transactionDate,
        BigDecimal amountUsd,
        String targetCurrency,
        BigDecimal exchangeRate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate exchangeRateDate,
        BigDecimal convertedAmount) {

    public static ConversionResponse of(ConversionResult r) {
        return new ConversionResponse(
                r.purchase().id().toString(),
                r.purchase().description(),
                r.purchase().transactionDate(),
                r.purchase().amountUsd().value(),
                r.rate().currency().value(),
                r.rate().rate(),
                r.rate().recordDate(),
                r.convertedAmount().value());
    }
}
