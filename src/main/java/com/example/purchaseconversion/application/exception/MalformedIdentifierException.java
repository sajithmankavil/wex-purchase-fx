package com.example.purchaseconversion.application.exception;

import java.util.Objects;

/**
 * Thrown when an inbound purchase-id string fails UUID v7 parsing.
 * Maps to HTTP 400 with {@code errorCode = MALFORMED_IDENTIFIER}
 * (component-design.md §4; AC-013).
 *
 * <p>Carries the raw input for the response body's {@code details.id} field.
 * The raw value is opaque (UUID-shaped); not subject to redaction.
 */
public final class MalformedIdentifierException extends DomainException {

    private final String input;

    public MalformedIdentifierException(String input) {
        super("malformed purchase identifier: " + Objects.requireNonNull(input));
        this.input = input;
    }

    public MalformedIdentifierException(String input, Throwable cause) {
        super("malformed purchase identifier: " + Objects.requireNonNull(input), cause);
        this.input = input;
    }

    public String getInput() {
        return input;
    }
}
