package com.example.purchaseconversion.application.exception;

import java.util.Objects;

/**
 * Thrown by {@code TreasuryClientPort.fetchRates(...)} on timeout / circuit-open /
 * 5xx after the retry budget / single-flight loser timeout (ADR-0001 D-9). Maps to
 * HTTP 503 with {@code errorCode = UPSTREAM_UNAVAILABLE} and a {@code Retry-After}
 * header (component-design.md §4; AC-023).
 *
 * <p>The {@code reason} string is a short stable label for metrics labelling and the
 * response body's {@code details.reason} (e.g., {@code "timeout"}, {@code "circuit_open"},
 * {@code "loser_timeout"}). Reasons are documented in observability.md.
 */
public final class UpstreamUnavailableException extends DomainException {

    private final String reason;

    public UpstreamUnavailableException(String reason) {
        super("treasury upstream unavailable: " + Objects.requireNonNull(reason));
        this.reason = reason;
    }

    public UpstreamUnavailableException(String reason, Throwable cause) {
        super("treasury upstream unavailable: " + Objects.requireNonNull(reason), cause);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
