package com.example.purchaseconversion.api.dto;

import com.example.purchaseconversion.application.conversion.ConversionResult;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Outbound payload for GET /api/v1/purchases/{id}/conversion (FR-003).
 *
 * <p>Per api-contracts.md §1 the {@code exchangeRate} is normalised to scale 6
 * on the wire (G4-P0-3 — e.g., Treasury's {@code "148.0"} surfaces as
 * {@code "148.000000"}). {@code convertedAmount} is scale 2 (cents, HALF_UP).
 */
@Schema(name = "ConversionResponse", description = "FR-003 conversion result with scale-6 exchangeRate (AC-014).")
public record ConversionResponse(
        @Schema(description = "Server-assigned purchase UUID v7.",
                example = "01900000-0000-7000-8000-000000000000")
        String id,
        @Schema(description = "Stored description verbatim.", example = "Coffee")
        String description,
        @Schema(description = "Purchase date (UTC).", example = "2026-05-10", format = "date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate transactionDate,
        @Schema(description = "USD amount, scale 2.", example = "123.45", type = "string")
        BigDecimal amountUsd,
        @Schema(description = "Canonical Treasury currency descriptor (post-alias-resolution).",
                example = "Canada-Dollar")
        String targetCurrency,
        @Schema(description = "Treasury exchange rate normalised to scale 6 (G4-P0-3).",
                example = "1.370000", type = "string", pattern = "^[0-9]+\\.[0-9]{6}$")
        BigDecimal exchangeRate,
        @Schema(description = "Treasury record_date associated with the selected rate.",
                example = "2026-04-15", format = "date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate exchangeRateDate,
        @Schema(description = "Converted amount = amountUsd × exchangeRate, scale 2, HALF_UP.",
                example = "169.13", type = "string")
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
