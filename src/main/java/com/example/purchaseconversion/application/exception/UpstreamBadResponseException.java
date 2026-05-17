package com.example.purchaseconversion.application.exception;

import java.util.Objects;

/**
 * Thrown by {@code TreasuryClientPort.fetchRates(...)} on a schema-invalid response
 * or a rate-sanity failure (rate ≤ 0 or rate > 1e30; Phase-6 G6-P1-3). Maps to HTTP
 * 502 with {@code errorCode = UPSTREAM_BAD_RESPONSE} (component-design.md §4;
 * AC-024 / AC-024b).
 *
 * <p>The {@code reason} string is a short stable label (e.g., {@code "schema_invalid"},
 * {@code "rate_sanity"}, {@code "orientation_drift"}).
 */
public final class UpstreamBadResponseException extends DomainException {

    private final String reason;

    public UpstreamBadResponseException(String reason) {
        super("treasury bad response: " + Objects.requireNonNull(reason));
        this.reason = reason;
    }

    public UpstreamBadResponseException(String reason, Throwable cause) {
        super("treasury bad response: " + Objects.requireNonNull(reason), cause);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
