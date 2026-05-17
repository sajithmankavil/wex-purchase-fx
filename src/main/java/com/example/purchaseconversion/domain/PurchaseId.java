package com.example.purchaseconversion.domain;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-generated purchase identifier (ADR-0001 D-7).
 *
 * <p>UUID v7 (RFC 9562) — time-ordered for index locality; opaque; globally unique.
 * Generated via {@code com.github.f4b6a3:uuid-creator} (Phase-4 G4-P0-4 closure).
 *
 * <p>Domain invariants:
 * <ul>
 *   <li>Non-null underlying {@link UUID}.</li>
 *   <li>{@link UUID#version()} == 7 — strict v7 only; v4/v1/etc. are rejected.</li>
 * </ul>
 *
 * <p>The canonical string form is the lowercase 8-4-4-4-12 hex layout, returned by
 * {@link UUID#toString()}. {@link #fromString(String)} parses any UUID that is both
 * syntactically valid and a v7.
 */
public record PurchaseId(UUID value) {

    public PurchaseId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.version() != 7) {
            throw new IllegalArgumentException(
                    "value must be a UUID v7; got version " + value.version() + " for " + value);
        }
    }

    /**
     * Generates a fresh UUID v7 (time-ordered).
     */
    public static PurchaseId next() {
        return new PurchaseId(UuidCreator.getTimeOrderedEpoch());
    }

    /**
     * Parses a UUID v7 from its canonical string form.
     *
     * @throws NullPointerException     if {@code s} is null
     * @throws IllegalArgumentException if {@code s} is not a syntactically valid UUID
     *                                  or its version is not 7
     */
    public static PurchaseId fromString(String s) {
        Objects.requireNonNull(s, "s must not be null");
        return new PurchaseId(UUID.fromString(s));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
