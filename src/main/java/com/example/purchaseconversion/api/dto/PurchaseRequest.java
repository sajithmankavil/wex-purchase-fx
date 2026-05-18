package com.example.purchaseconversion.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inbound POST /api/v1/purchases body (FR-001).
 *
 * <p>Bean-validation enforces: non-blank description ≤ 50 chars (AC-002 / AC-003);
 * non-null UTC date (no time zone; AC-014); strictly positive amount with scale ≤ 2
 * (AC-008). The application-layer {@code Purchase} record re-enforces all of these
 * as a defensive belt-and-suspenders pair with the Bean Validation layer.
 */
public record PurchaseRequest(
        @NotBlank(message = "description must not be blank")
        @Size(max = 50, message = "description must be at most 50 characters")
        String description,

        @NotNull(message = "transactionDate must not be null")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate transactionDate,

        @NotNull(message = "amountUsd must not be null")
        @DecimalMin(value = "0.01", message = "amountUsd must be strictly positive")
        @Digits(integer = 17, fraction = 2, message = "amountUsd must have at most 2 fractional digits")
        BigDecimal amountUsd) {
}
