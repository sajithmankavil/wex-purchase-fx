package com.example.purchaseconversion.application.conversion;

import com.example.purchaseconversion.application.exception.ConversionRateNotAvailableException;
import com.example.purchaseconversion.application.exception.InvalidCurrencyException;
import com.example.purchaseconversion.application.exception.PurchaseNotFoundException;
import com.example.purchaseconversion.application.exception.UpstreamBadResponseException;
import com.example.purchaseconversion.application.exception.UpstreamUnavailableException;
import com.example.purchaseconversion.application.port.in.ConvertPurchaseUseCase;
import com.example.purchaseconversion.application.port.out.CurrencyAliasPort;
import com.example.purchaseconversion.application.port.out.ExchangeRateHotCachePort;
import com.example.purchaseconversion.application.port.out.ExchangeRateRepositoryPort;
import com.example.purchaseconversion.application.port.out.PurchaseRepositoryPort;
import com.example.purchaseconversion.application.port.out.TreasuryClientPort;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.domain.ExchangeRate;
import com.example.purchaseconversion.domain.Purchase;
import com.example.purchaseconversion.domain.PurchaseId;
import com.example.purchaseconversion.domain.RateSelectionPolicy;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for FR-003 (convert purchase to target currency).
 *
 * <p>Implements {@link ConvertPurchaseUseCase}. Orchestrates the conversion flow per
 * component-design.md §3.2:
 *
 * <ol>
 *   <li>Resolve {@code currencyInput} via {@link CurrencyAliasPort} (AC-021b/c).</li>
 *   <li>Look up the {@link Purchase} via {@link PurchaseRepositoryPort} (AC-008).</li>
 *   <li>Compute the 6-month window {@code [txDate.minusMonths(6), txDate]} per
 *       ADR-0001 D-5 (Java's {@code LocalDate} end-of-month clamp).</li>
 *   <li>Try the hot cache first ({@link ExchangeRateHotCachePort}); apply
 *       {@link RateSelectionPolicy}.</li>
 *   <li>On miss, try the DB ({@link ExchangeRateRepositoryPort}); apply the policy;
 *       populate the hot cache on hit.</li>
 *   <li>On miss, fetch from {@link TreasuryClientPort}; upsert versioned rows into the
 *       DB; re-read from DB; apply the policy; populate the hot cache on hit.</li>
 *   <li>On final miss, throw {@link ConversionRateNotAvailableException} (AC-020).</li>
 *   <li>Multiply with {@code Money.multiply(rate.rate())} — HALF_UP at scale 2 per D-6.</li>
 * </ol>
 *
 * <p>The single-flight gate (D-9) is invisible at this layer — it is encapsulated inside
 * {@link TreasuryClientPort}'s adapter. {@link UpstreamUnavailableException} /
 * {@link UpstreamBadResponseException} propagate untouched from the port.
 *
 * <p>Pure POJO — no Spring annotations, no I/O.
 */
public final class ConversionService implements ConvertPurchaseUseCase {

    private final PurchaseRepositoryPort purchaseRepository;
    private final ExchangeRateRepositoryPort exchangeRateRepository;
    private final ExchangeRateHotCachePort hotCache;
    private final TreasuryClientPort treasuryClient;
    private final CurrencyAliasPort aliasPort;

    public ConversionService(
            PurchaseRepositoryPort purchaseRepository,
            ExchangeRateRepositoryPort exchangeRateRepository,
            ExchangeRateHotCachePort hotCache,
            TreasuryClientPort treasuryClient,
            CurrencyAliasPort aliasPort) {
        this.purchaseRepository = Objects.requireNonNull(purchaseRepository, "purchaseRepository must not be null");
        this.exchangeRateRepository = Objects.requireNonNull(exchangeRateRepository, "exchangeRateRepository must not be null");
        this.hotCache = Objects.requireNonNull(hotCache, "hotCache must not be null");
        this.treasuryClient = Objects.requireNonNull(treasuryClient, "treasuryClient must not be null");
        this.aliasPort = Objects.requireNonNull(aliasPort, "aliasPort must not be null");
    }

    @Override
    public ConversionResult convert(PurchaseId id, String currencyInput) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(currencyInput, "currencyInput must not be null");

        CurrencyDescriptor canonical = aliasPort.resolve(currencyInput)
                .orElseThrow(() -> new InvalidCurrencyException(currencyInput));

        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> new PurchaseNotFoundException(id));

        LocalDate windowLower = purchase.transactionDate().minusMonths(RateSelectionPolicy.LOOKBACK_MONTHS);
        LocalDate windowUpper = purchase.transactionDate();

        // 1. Hot cache.
        Optional<ExchangeRate> eligible = selectEligible(
                purchase, canonical,
                hotCache.findInWindow(canonical, windowLower, windowUpper));
        if (eligible.isPresent()) {
            return buildResult(purchase, eligible.get());
        }

        // 2. DB.
        List<ExchangeRate> dbRates = exchangeRateRepository.findInWindow(canonical, windowLower, windowUpper);
        eligible = selectEligible(purchase, canonical, dbRates);
        if (eligible.isPresent()) {
            hotCache.putAll(dbRates);
            return buildResult(purchase, eligible.get());
        }

        // 3. Treasury fetch + upsert. Throws UpstreamUnavailable / UpstreamBadResponse on failure.
        List<ExchangeRate> fetched = treasuryClient.fetchRates(canonical, windowLower, windowUpper);
        exchangeRateRepository.upsertVersioned(fetched);

        // 4. Re-read DB after upsert (AC-026b: persistence-centric idempotency — the canonical
        //    state is the DB, not the fetched list).
        List<ExchangeRate> rebuiltRates = exchangeRateRepository.findInWindow(canonical, windowLower, windowUpper);
        eligible = selectEligible(purchase, canonical, rebuiltRates);
        if (eligible.isEmpty()) {
            throw new ConversionRateNotAvailableException(
                    purchase.transactionDate(), canonical, windowLower, windowUpper);
        }
        hotCache.putAll(rebuiltRates);
        return buildResult(purchase, eligible.get());
    }

    private static Optional<ExchangeRate> selectEligible(
            Purchase purchase, CurrencyDescriptor canonical, List<ExchangeRate> candidates) {
        return RateSelectionPolicy.selectEligibleRate(
                purchase.transactionDate(), canonical, candidates);
    }

    private static ConversionResult buildResult(Purchase purchase, ExchangeRate rate) {
        return new ConversionResult(
                purchase, rate, purchase.amountUsd().multiply(rate.rate()));
    }
}
