package com.example.purchaseconversion.api.advice.exception;

import com.example.purchaseconversion.application.exception.DomainException;

import java.util.Objects;

/**
 * Thrown by {@code ContentGuard} when the {@code description} (or one of its
 * decoded variants) matches a PAN-Luhn or track-data pattern (ADR-0001 D-13;
 * AC-010b / AC-010c / AC-010d / AC-010e).
 *
 * <p>The {@code reason} label is one of {@code "luhn"}, {@code "luhn-encoded"},
 * {@code "track1"}, {@code "track2"}. The rejected payload is NEVER logged.
 */
public final class PanPatternDetectedException extends DomainException {

    private final String reason;

    public PanPatternDetectedException(String reason) {
        super("PAN-shaped content detected: " + Objects.requireNonNull(reason));
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
