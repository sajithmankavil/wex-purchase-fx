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
 * the A2 §5 treatment of {@code InvalidCurrencyException}).
 *
 * <p><b>Defense-in-depth on the exception message itself</b> — Phase 11 closure
 * of {@code malformed-identifier-exception-message-redaction} (C3 30-review §F5;
 * Phase 10 30-review §C2): the {@code super(...)} message no longer embeds the
 * raw input. Only the {@code length} signal is preserved (defensible for
 * debugging — length 16 = PAN-shaped attack; length 36 = malformed-UUID typo).
 * Callers that log {@code e.getMessage()} via the catch-all
 * {@code @ExceptionHandler(Throwable.class)} therefore cannot leak the raw
 * value even if a future refactor removes the specific
 * {@code ProblemDetailExceptionHandler::onMalformedId} handler. The raw input
 * remains accessible via {@link #getInput()} for the centralised handler that
 * needs to hash it for the response body; any other reader of the exception
 * (logger of {@code getMessage()}, stack trace serializer) sees only the length.
 *
 * <p>Closes C 30-review §4.7 (input hashing on emit — C3) AND Phase 10
 * 30-review §C2 (defense-in-depth message redaction — this commit).
 */
public final class MalformedIdentifierException extends DomainException {

    private final String input;

    public MalformedIdentifierException(String input) {
        super(buildMessage(input));
        this.input = input;
    }

    public MalformedIdentifierException(String input, Throwable cause) {
        super(buildMessage(input), cause);
        this.input = input;
    }

    public String getInput() {
        return input;
    }

    private static String buildMessage(String input) {
        Objects.requireNonNull(input);
        return "malformed purchase identifier (length=" + input.length() + ")";
    }
}
