package com.example.purchaseconversion.application.conversion;

import com.example.purchaseconversion.application.exception.ConversionRateNotAvailableException;
import com.example.purchaseconversion.application.exception.InvalidCurrencyException;
import com.example.purchaseconversion.application.exception.PurchaseNotFoundException;
import com.example.purchaseconversion.application.exception.UpstreamBadResponseException;
import com.example.purchaseconversion.application.exception.UpstreamUnavailableException;
import com.example.purchaseconversion.application.port.out.CurrencyAliasPort;
import com.example.purchaseconversion.application.port.out.ExchangeRateHotCachePort;
import com.example.purchaseconversion.application.port.out.ExchangeRateRepositoryPort;
import com.example.purchaseconversion.application.port.out.PurchaseRepositoryPort;
import com.example.purchaseconversion.application.port.out.TreasuryClientPort;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.domain.ExchangeRate;
import com.example.purchaseconversion.domain.Money;
import com.example.purchaseconversion.domain.Purchase;
import com.example.purchaseconversion.domain.PurchaseId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversionServiceTest {

    private static final CurrencyDescriptor CAD = CurrencyDescriptor.of("Canada-Dollar");
    private static final CurrencyDescriptor EUR = CurrencyDescriptor.of("Euro Zone-Euro");
    private static final LocalDate TX_DATE = LocalDate.of(2026, 5, 17);

    @Mock
    private PurchaseRepositoryPort purchaseRepository;
    @Mock
    private ExchangeRateRepositoryPort exchangeRateRepository;
    @Mock
    private ExchangeRateHotCachePort hotCache;
    @Mock
    private TreasuryClientPort treasuryClient;
    @Mock
    private CurrencyAliasPort aliasPort;

    private ConversionService service;

    private Purchase purchase;
    private LocalDate windowLower;
    private LocalDate windowUpper;

    @BeforeEach
    void setUp() {
        service = new ConversionService(
                purchaseRepository, exchangeRateRepository, hotCache, treasuryClient, aliasPort);
        purchase = new Purchase(
                PurchaseId.next(), "Coffee", TX_DATE, Money.of("123.45"));
        windowLower = TX_DATE.minusMonths(6);
        windowUpper = TX_DATE;
    }

    // ---------------------------------------------------------------------------
    // Layer order: hot cache → DB → Treasury
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("Layer ordering — hot cache → DB → Treasury")
    class LayerOrdering {

        @Test
        @DisplayName("hot-cache hit short-circuits DB and Treasury")
        void hotCacheHit() {
            ExchangeRate rate = rate(CAD, TX_DATE.minusDays(1), TX_DATE.minusDays(1), "1.3700");
            when(aliasPort.resolve("CAD")).thenReturn(Optional.of(CAD));
            when(purchaseRepository.findById(purchase.id())).thenReturn(Optional.of(purchase));
            when(hotCache.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of(rate));

            ConversionResult result = service.convert(purchase.id(), "CAD");

            assertThat(result.rate()).isEqualTo(rate);
            assertThat(result.convertedAmount()).isEqualTo(Money.of("169.13"));
            verifyNoInteractions(exchangeRateRepository);
            verifyNoInteractions(treasuryClient);
        }

        @Test
        @DisplayName("hot-cache miss + DB hit: populates cache; no Treasury call")
        void cacheMissDbHit() {
            ExchangeRate rate = rate(CAD, TX_DATE.minusDays(2), TX_DATE.minusDays(2), "1.4000");
            when(aliasPort.resolve("CAD")).thenReturn(Optional.of(CAD));
            when(purchaseRepository.findById(purchase.id())).thenReturn(Optional.of(purchase));
            when(hotCache.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of());
            when(exchangeRateRepository.findInWindow(CAD, windowLower, windowUpper))
                    .thenReturn(List.of(rate));

            ConversionResult result = service.convert(purchase.id(), "CAD");

            assertThat(result.rate()).isEqualTo(rate);
            assertThat(result.convertedAmount()).isEqualTo(Money.of("172.83"));
            verify(hotCache).putAll(List.of(rate));
            verifyNoInteractions(treasuryClient);
        }

        @Test
        @DisplayName("cache + DB miss → Treasury fetch + upsert + DB re-read → success")
        void cacheMissDbMissTreasurySuccess() {
            ExchangeRate fetched = rate(CAD, TX_DATE.minusDays(3), TX_DATE.minusDays(3), "1.3500");
            when(aliasPort.resolve("CAD")).thenReturn(Optional.of(CAD));
            when(purchaseRepository.findById(purchase.id())).thenReturn(Optional.of(purchase));
            when(hotCache.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of());
            when(exchangeRateRepository.findInWindow(CAD, windowLower, windowUpper))
                    .thenReturn(List.of())                  // initial DB miss
                    .thenReturn(List.of(fetched));          // after upsert
            when(treasuryClient.fetchRates(CAD, windowLower, windowUpper)).thenReturn(List.of(fetched));

            ConversionResult result = service.convert(purchase.id(), "CAD");

            assertThat(result.rate()).isEqualTo(fetched);
            assertThat(result.convertedAmount()).isEqualTo(Money.of("166.66"));

            InOrder inOrder = inOrder(hotCache, exchangeRateRepository, treasuryClient);
            inOrder.verify(hotCache).findInWindow(CAD, windowLower, windowUpper);
            inOrder.verify(exchangeRateRepository).findInWindow(CAD, windowLower, windowUpper);
            inOrder.verify(treasuryClient).fetchRates(CAD, windowLower, windowUpper);
            inOrder.verify(exchangeRateRepository).upsertVersioned(List.of(fetched));
            inOrder.verify(exchangeRateRepository).findInWindow(CAD, windowLower, windowUpper);
            inOrder.verify(hotCache).putAll(List.of(fetched));
        }

        @Test
        @DisplayName("AC-020 — cache + DB miss + Treasury returns empty: ConversionRateNotAvailable")
        void cacheMissDbMissTreasuryEmpty() {
            when(aliasPort.resolve("CAD")).thenReturn(Optional.of(CAD));
            when(purchaseRepository.findById(purchase.id())).thenReturn(Optional.of(purchase));
            when(hotCache.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of());
            when(exchangeRateRepository.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of());
            when(treasuryClient.fetchRates(CAD, windowLower, windowUpper)).thenReturn(List.of());

            assertThatThrownBy(() -> service.convert(purchase.id(), "CAD"))
                    .isInstanceOf(ConversionRateNotAvailableException.class)
                    .satisfies(t -> {
                        ConversionRateNotAvailableException e = (ConversionRateNotAvailableException) t;
                        assertThat(e.getPurchaseDate()).isEqualTo(TX_DATE);
                        assertThat(e.getTargetCurrency()).isEqualTo(CAD);
                        assertThat(e.getWindowLower()).isEqualTo(windowLower);
                        assertThat(e.getWindowUpper()).isEqualTo(windowUpper);
                    });
            // hot cache is NOT populated after a failed fetch.
            verify(hotCache, never()).putAll(any());
        }

        @Test
        @DisplayName("AC-022b — Treasury returns rates outside window: still ConversionRateNotAvailable after upsert")
        void treasuryReturnsRatesOutsideWindow() {
            ExchangeRate stale = rate(CAD, TX_DATE.minusYears(2), TX_DATE.minusYears(2), "1.3500");
            when(aliasPort.resolve("CAD")).thenReturn(Optional.of(CAD));
            when(purchaseRepository.findById(purchase.id())).thenReturn(Optional.of(purchase));
            when(hotCache.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of());
            when(exchangeRateRepository.findInWindow(CAD, windowLower, windowUpper))
                    .thenReturn(List.of())
                    .thenReturn(List.of(stale));  // returns rates that the DB layer keeps but the policy rejects
            when(treasuryClient.fetchRates(CAD, windowLower, windowUpper)).thenReturn(List.of(stale));

            assertThatThrownBy(() -> service.convert(purchase.id(), "CAD"))
                    .isInstanceOf(ConversionRateNotAvailableException.class);
            verify(exchangeRateRepository).upsertVersioned(List.of(stale));
        }
    }

    // ---------------------------------------------------------------------------
    // Currency alias resolution (AC-021b/c)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("Currency alias resolution (AC-021b/c)")
    class AliasResolution {

        @Test
        @DisplayName("AC-021b — unknown currency input raises InvalidCurrencyException")
        void unknownCurrencyInputRaises() {
            when(aliasPort.resolve("XXX")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.convert(purchase.id(), "XXX"))
                    .isInstanceOf(InvalidCurrencyException.class)
                    .satisfies(t -> assertThat(((InvalidCurrencyException) t).getCurrency()).isEqualTo("XXX"));
            verifyNoInteractions(purchaseRepository);
            verifyNoInteractions(exchangeRateRepository);
            verifyNoInteractions(hotCache);
            verifyNoInteractions(treasuryClient);
        }

        @Test
        @DisplayName("ISO code resolves to canonical descriptor; flow proceeds")
        void isoCodeResolves() {
            ExchangeRate rate = rate(EUR, TX_DATE.minusDays(1), TX_DATE.minusDays(1), "0.9200");
            when(aliasPort.resolve("EUR")).thenReturn(Optional.of(EUR));
            when(purchaseRepository.findById(purchase.id())).thenReturn(Optional.of(purchase));
            when(hotCache.findInWindow(EUR, windowLower, windowUpper)).thenReturn(List.of(rate));

            ConversionResult result = service.convert(purchase.id(), "EUR");

            assertThat(result.rate().currency()).isEqualTo(EUR);
        }
    }

    // ---------------------------------------------------------------------------
    // Purchase lookup (AC-008)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("Purchase lookup")
    class PurchaseLookup {

        @Test
        @DisplayName("AC-008 — missing purchase id raises PurchaseNotFoundException")
        void missingPurchaseRaises() {
            PurchaseId missing = PurchaseId.next();
            when(aliasPort.resolve("CAD")).thenReturn(Optional.of(CAD));
            when(purchaseRepository.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.convert(missing, "CAD"))
                    .isInstanceOf(PurchaseNotFoundException.class)
                    .satisfies(t -> assertThat(((PurchaseNotFoundException) t).getId()).isEqualTo(missing));
            verifyNoInteractions(hotCache);
            verifyNoInteractions(exchangeRateRepository);
            verifyNoInteractions(treasuryClient);
        }
    }

    // ---------------------------------------------------------------------------
    // Treasury exception propagation
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("Treasury exceptions propagate untouched")
    class TreasuryFailures {

        @Test
        @DisplayName("AC-023 — UpstreamUnavailableException propagates from Treasury")
        void upstreamUnavailable() {
            when(aliasPort.resolve("CAD")).thenReturn(Optional.of(CAD));
            when(purchaseRepository.findById(purchase.id())).thenReturn(Optional.of(purchase));
            when(hotCache.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of());
            when(exchangeRateRepository.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of());
            when(treasuryClient.fetchRates(CAD, windowLower, windowUpper))
                    .thenThrow(new UpstreamUnavailableException("timeout"));

            assertThatThrownBy(() -> service.convert(purchase.id(), "CAD"))
                    .isInstanceOf(UpstreamUnavailableException.class)
                    .satisfies(t -> assertThat(((UpstreamUnavailableException) t).getReason()).isEqualTo("timeout"));
            verify(exchangeRateRepository, never()).upsertVersioned(any());
        }

        @Test
        @DisplayName("AC-024 — UpstreamBadResponseException propagates from Treasury")
        void upstreamBadResponse() {
            when(aliasPort.resolve("CAD")).thenReturn(Optional.of(CAD));
            when(purchaseRepository.findById(purchase.id())).thenReturn(Optional.of(purchase));
            when(hotCache.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of());
            when(exchangeRateRepository.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of());
            when(treasuryClient.fetchRates(CAD, windowLower, windowUpper))
                    .thenThrow(new UpstreamBadResponseException("schema_invalid"));

            assertThatThrownBy(() -> service.convert(purchase.id(), "CAD"))
                    .isInstanceOf(UpstreamBadResponseException.class)
                    .satisfies(t -> assertThat(((UpstreamBadResponseException) t).getReason()).isEqualTo("schema_invalid"));
            verify(exchangeRateRepository, never()).upsertVersioned(any());
        }
    }

    // ---------------------------------------------------------------------------
    // Rate selection re-exercised via ConversionService
    // (AC-014..AC-020 + AC-018b + AC-019b)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("Rate selection through ConversionService (AC-014..AC-020 + AC-018b + AC-019b)")
    class RateSelection {

        @Test
        @DisplayName("AC-014 — exact-date rate selected when present")
        void ac014_exactDate() {
            ExchangeRate exact = rate(CAD, TX_DATE, TX_DATE, "1.3700");
            ExchangeRate older = rate(CAD, TX_DATE.minusMonths(2), TX_DATE.minusMonths(2), "1.3500");
            stubResolveAndPurchase();
            when(hotCache.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of(older, exact));

            ConversionResult result = service.convert(purchase.id(), "CAD");

            assertThat(result.rate()).isEqualTo(exact);
        }

        @Test
        @DisplayName("AC-015 — most-recent rate within 6 months selected when no exact match")
        void ac015_mostRecent() {
            ExchangeRate older = rate(CAD, TX_DATE.minusMonths(4), TX_DATE.minusMonths(4), "1.3000");
            ExchangeRate newer = rate(CAD, TX_DATE.minusMonths(1), TX_DATE.minusMonths(1), "1.3700");
            stubResolveAndPurchase();
            when(hotCache.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of(older, newer));

            ConversionResult result = service.convert(purchase.id(), "CAD");

            assertThat(result.rate()).isEqualTo(newer);
        }

        @Test
        @DisplayName("AC-018 — exactly 6 months before is eligible (inclusive)")
        void ac018_sixMonthsBeforeEligible() {
            LocalDate sixMonthsBefore = TX_DATE.minusMonths(6);
            ExchangeRate boundary = rate(CAD, sixMonthsBefore, sixMonthsBefore, "1.3300");
            stubResolveAndPurchase();
            when(hotCache.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of(boundary));

            ConversionResult result = service.convert(purchase.id(), "CAD");

            assertThat(result.rate()).isEqualTo(boundary);
        }

        @Test
        @DisplayName("AC-018b — EOM-clamp boundary eligible (LocalDate.minusMonths semantics)")
        void ac018b_eomClampEligible() {
            Purchase aug31 = new Purchase(PurchaseId.next(), "Coffee",
                    LocalDate.of(2026, 8, 31), Money.of("100.00"));
            LocalDate window = LocalDate.of(2026, 8, 31);
            LocalDate windowLower = LocalDate.of(2026, 2, 28); // Java's EOM clamp
            ExchangeRate atClamp = rate(CAD, windowLower, windowLower, "1.3000");
            when(aliasPort.resolve("CAD")).thenReturn(Optional.of(CAD));
            when(purchaseRepository.findById(aug31.id())).thenReturn(Optional.of(aug31));
            when(hotCache.findInWindow(CAD, windowLower, window)).thenReturn(List.of(atClamp));

            ConversionResult result = service.convert(aug31.id(), "CAD");

            assertThat(result.rate()).isEqualTo(atClamp);
        }

        @Test
        @DisplayName("AC-019 — one day past 6 months is ineligible (out of window)")
        void ac019_justOutsideIneligible() {
            ExchangeRate stale = rate(CAD, TX_DATE.minusMonths(6).minusDays(1),
                    TX_DATE.minusMonths(6).minusDays(1), "1.3300");
            stubResolveAndPurchase();
            when(hotCache.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of());
            // The hot-cache port's contract is filtered findInWindow; we model that by
            // returning empty when the rate is outside [windowLower, windowUpper]. The DB
            // port is similarly modelled.
            when(exchangeRateRepository.findInWindow(CAD, windowLower, windowUpper))
                    .thenReturn(List.of())
                    .thenReturn(List.of());
            when(treasuryClient.fetchRates(CAD, windowLower, windowUpper)).thenReturn(List.of(stale));

            assertThatThrownBy(() -> service.convert(purchase.id(), "CAD"))
                    .isInstanceOf(ConversionRateNotAvailableException.class);
        }

        @Test
        @DisplayName("AC-019b — one day past EOM-clamp is ineligible")
        void ac019b_eomClampIneligible() {
            Purchase aug31 = new Purchase(PurchaseId.next(), "Coffee",
                    LocalDate.of(2026, 8, 31), Money.of("100.00"));
            LocalDate window = LocalDate.of(2026, 8, 31);
            LocalDate windowLower = LocalDate.of(2026, 2, 28);
            LocalDate justOutside = LocalDate.of(2026, 2, 27);
            ExchangeRate stale = rate(CAD, justOutside, justOutside, "1.3000");
            when(aliasPort.resolve("CAD")).thenReturn(Optional.of(CAD));
            when(purchaseRepository.findById(aug31.id())).thenReturn(Optional.of(aug31));
            // findInWindow correctly filters to [2026-02-28, 2026-08-31] so a 2026-02-27 row is out.
            when(hotCache.findInWindow(CAD, windowLower, window)).thenReturn(List.of());
            when(exchangeRateRepository.findInWindow(CAD, windowLower, window))
                    .thenReturn(List.of())
                    .thenReturn(List.of());
            when(treasuryClient.fetchRates(CAD, windowLower, window)).thenReturn(List.of(stale));

            assertThatThrownBy(() -> service.convert(aug31.id(), "CAD"))
                    .isInstanceOf(ConversionRateNotAvailableException.class);
        }

        @Test
        @DisplayName("OQ-002 — tie on recordDate broken by max(effectiveDate)")
        void oq002_tieBreakOnEffectiveDate() {
            LocalDate sharedRecordDate = TX_DATE.minusMonths(1);
            ExchangeRate original = rate(CAD, sharedRecordDate, sharedRecordDate, "1.3500");
            ExchangeRate revision = rate(CAD, sharedRecordDate, sharedRecordDate.plusDays(5), "1.3600");
            stubResolveAndPurchase();
            when(hotCache.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of(original, revision));

            ConversionResult result = service.convert(purchase.id(), "CAD");

            assertThat(result.rate()).isEqualTo(revision);
        }

        @Test
        @DisplayName("Other-currency rates in the window are filtered out by the policy")
        void filtersOtherCurrencies() {
            ExchangeRate cad = rate(CAD, TX_DATE.minusMonths(1), TX_DATE.minusMonths(1), "1.3700");
            ExchangeRate eur = rate(EUR, TX_DATE.minusDays(1), TX_DATE.minusDays(1), "0.9200");
            stubResolveAndPurchase();
            when(hotCache.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of(cad, eur));

            ConversionResult result = service.convert(purchase.id(), "CAD");

            assertThat(result.rate()).isEqualTo(cad);
        }
    }

    // ---------------------------------------------------------------------------
    // Monetary correctness (AC-025; D-4; D-6)
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("Monetary correctness")
    class Money_ {

        @Test
        @DisplayName("AC-025 — multiplicative conversion + HALF_UP at scale 2")
        void halfUpAtScaleTwo() {
            ExchangeRate rate = rate(CAD, TX_DATE.minusDays(1), TX_DATE.minusDays(1), "1.3700");
            stubResolveAndPurchase();
            when(hotCache.findInWindow(CAD, windowLower, windowUpper)).thenReturn(List.of(rate));

            ConversionResult result = service.convert(purchase.id(), "CAD");

            // 123.45 * 1.3700 = 169.1265 → HALF_UP → 169.13 (D-6, AC-025).
            assertThat(result.convertedAmount()).isEqualTo(Money.of("169.13"));
        }

        @Test
        @DisplayName("HALF_UP rounds .5 cases up")
        void halfUpRoundsHalfUp() {
            Purchase p = new Purchase(PurchaseId.next(), "round", TX_DATE, Money.of("1.00"));
            ExchangeRate r = rate(CAD, TX_DATE, TX_DATE, "0.005"); // 1.00 * 0.005 = 0.005 → 0.01
            when(aliasPort.resolve("CAD")).thenReturn(Optional.of(CAD));
            when(purchaseRepository.findById(p.id())).thenReturn(Optional.of(p));
            when(hotCache.findInWindow(CAD, p.transactionDate().minusMonths(6), p.transactionDate()))
                    .thenReturn(List.of(r));

            ConversionResult result = service.convert(p.id(), "CAD");

            assertThat(result.convertedAmount()).isEqualTo(Money.of("0.01"));
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private void stubResolveAndPurchase() {
        when(aliasPort.resolve("CAD")).thenReturn(Optional.of(CAD));
        when(purchaseRepository.findById(purchase.id())).thenReturn(Optional.of(purchase));
    }

    private static ExchangeRate rate(CurrencyDescriptor currency, LocalDate recordDate, LocalDate effectiveDate, String rate) {
        return new ExchangeRate(currency, recordDate, effectiveDate, new BigDecimal(rate));
    }
}
