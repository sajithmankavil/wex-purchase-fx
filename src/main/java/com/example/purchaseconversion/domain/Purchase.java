package com.example.purchaseconversion.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Stored purchase transaction (FR-001).
 *
 * <p>Aggregate root of the purchase aggregate. Stores a USD amount, a transaction date, a
 * free-text description, and a server-generated identifier.
 *
 * <p>Domain invariants:
 * <ul>
 *   <li>Every field is non-null.</li>
 *   <li>{@code description} is non-blank and ≤ 50 characters (UTF-16 code units, per
 *       A-013; source rule: "Description: must not exceed 50 characters").</li>
 *   <li>{@code transactionDate} carries no time and no zone (LocalDate).</li>
 *   <li>{@code amountUsd} is strictly positive and scale 2 (enforced by {@link Money}).</li>
 * </ul>
 *
 * <p>Notes on responsibilities not in this type:
 * <ul>
 *   <li>Future-date rejection (A-002 / AC-006) is an application-layer concern that uses
 *       an injected {@link java.time.Clock}; the domain is Clock-free.</li>
 *   <li>PAN / track-data / encoded-PAN content guards (AC-010b/c/d/e) live in the API layer's
 *       {@code ContentGuard} advice, not here. The domain assumes valid input has already
 *       passed those guards.</li>
 * </ul>
 */
public record Purchase(
        PurchaseId id,
        String description,
        LocalDate transactionDate,
        Money amountUsd) {

    /** Source-rule limit: "Description: must not exceed 50 characters" (FR-001). */
    public static final int DESCRIPTION_MAX_LENGTH = 50;

    public Purchase {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(transactionDate, "transactionDate must not be null");
        Objects.requireNonNull(amountUsd, "amountUsd must not be null");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "description length must be <= " + DESCRIPTION_MAX_LENGTH
                            + "; got " + description.length());
        }
    }
}
