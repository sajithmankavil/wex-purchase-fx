package com.example.purchaseconversion.application.port.in;

import com.example.purchaseconversion.domain.Money;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Inbound command for purchase registration (FR-001).
 *
 * <p>Carries the controller-validated request data into the application layer. All
 * primitive-validation (non-null, length, scale-2) has already been performed by the
 * API layer's bean validation + ContentGuard (Chunk C). The application layer applies
 * the FR-001 business rule {@code transactionDate ≤ today} via {@code ClockPort} and
 * delegates the rest to the {@link com.example.purchaseconversion.domain.Purchase} constructor's
 * invariants.
 */
public record RegisterPurchaseCommand(
        String description,
        LocalDate transactionDate,
        Money amountUsd) {

    public RegisterPurchaseCommand {
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(transactionDate, "transactionDate must not be null");
        Objects.requireNonNull(amountUsd, "amountUsd must not be null");
    }
}
