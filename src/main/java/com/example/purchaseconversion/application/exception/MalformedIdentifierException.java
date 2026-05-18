package com.example.purchaseconversion.application.exception;

import java.util.Objects;

/**
 * Thrown when an inbound purchase-id string fails UUID v7 parsing.
 * Maps to HTTP 400 with {@code errorCode = MALFORMED_IDENTIFIER}
 * (component-design.md §4; AC-013).
 *
 * <p>Carries the raw user-supplied input via {@link #getInput()}. Because the
 * failure mode is "input was not UUID-shaped", the input is arbitrary
 * attacker-controlled text — including Luhn-valid PAN-shaped sequences.
 * The advice handler MUST pass the input through {@code DescriptionHasher}
 * before any log emission and MUST omit the raw value from the response
 * body's {@code details.id} field (carrying only the hash + length, mirroring
 * the A2 §5 treatment of {@code InvalidCurrencyException}). The exception's
 * {@code getMessage()} also contains the raw input; callers that log
 * {@code e.getMessage()} would leak it — the centralised handler
 * {@code ProblemDetailExceptionHandler::onMalformedId} avoids this by logging
 * only the hashed form.
 *
 * <p>Closes C 30-review §4.7 (NEW MED — `malformed-identifier-input-hashing`).
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
