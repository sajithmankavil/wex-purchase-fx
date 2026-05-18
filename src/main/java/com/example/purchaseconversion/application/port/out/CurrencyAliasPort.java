package com.example.purchaseconversion.application.port.out;

import com.example.purchaseconversion.domain.CurrencyDescriptor;

import java.util.Optional;

/**
 * Outbound port for currency-input → canonical descriptor resolution
 * (ADR-0001 D-8; component-design.md §3.2; AC-021b/c).
 *
 * <p>Accepts either an ISO-4217 three-letter code (e.g., {@code "CAD"}, {@code "EUR"})
 * or a case-insensitive Treasury descriptor (e.g., {@code "canada-dollar"},
 * {@code "Euro Zone-Euro"}). Returns the canonical
 * {@link CurrencyDescriptor} on hit, {@link Optional#empty()} on miss. The application
 * layer maps an empty result to {@code InvalidCurrencyException} →
 * {@code 400 INVALID_CURRENCY}.
 *
 * <p>Alias-table drift handling (AC-021b/c) is the adapter's responsibility (emits the
 * {@code currency_alias.drift.detected.count} metric). The application layer is
 * orthogonal to drift — it just sees resolve vs no-resolve.
 */
public interface CurrencyAliasPort {

    /**
     * Resolves the given currency input to the canonical Treasury descriptor.
     */
    Optional<CurrencyDescriptor> resolve(String input);
}
