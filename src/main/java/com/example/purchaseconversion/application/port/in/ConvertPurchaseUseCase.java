package com.example.purchaseconversion.application.port.in;

import com.example.purchaseconversion.application.conversion.ConversionResult;
import com.example.purchaseconversion.application.exception.ConversionRateNotAvailableException;
import com.example.purchaseconversion.application.exception.InvalidCurrencyException;
import com.example.purchaseconversion.application.exception.PurchaseNotFoundException;
import com.example.purchaseconversion.application.exception.UpstreamBadResponseException;
import com.example.purchaseconversion.application.exception.UpstreamUnavailableException;
import com.example.purchaseconversion.domain.PurchaseId;

/**
 * Inbound use-case interface for purchase conversion (FR-003; AC-014..AC-027).
 *
 * <p>Implemented by {@code application.conversion.ConversionService}.
 */
public interface ConvertPurchaseUseCase {

    /**
     * Converts the stored purchase into the requested target currency using the 6-month
     * rate-selection rule (ADR-0001 D-5). On cache + local-store miss, fetches from the
     * Treasury Fiscal Data API via {@code TreasuryClientPort} and persists the result.
     *
     * @param id                purchase identifier (UUID v7)
     * @param currencyInput     ISO-4217 code or Treasury descriptor (case-insensitive)
     * @return immutable conversion result containing the source purchase, the selected
     *         rate, and the converted amount (scale 2, HALF_UP)
     * @throws InvalidCurrencyException             alias resolution miss (AC-021b/c)
     * @throws PurchaseNotFoundException            no purchase with that id
     * @throws ConversionRateNotAvailableException  no rate in the 6-month window even
     *                                              after fetching from Treasury
     * @throws UpstreamUnavailableException         Treasury timeout / circuit-open / 5xx
     * @throws UpstreamBadResponseException         Treasury schema-invalid or sanity-failed
     */
    ConversionResult convert(PurchaseId id, String currencyInput);
}
