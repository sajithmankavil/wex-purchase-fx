package com.example.purchaseconversion.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateSelectionPolicyTest {

    private static final CurrencyDescriptor CAD = CurrencyDescriptor.of("Canada-Dollar");
    private static final CurrencyDescriptor EUR = CurrencyDescriptor.of("Euro Zone-Euro");

    /** Helper: build a rate with effectiveDate == recordDate (the common case in Treasury data). */
    private static ExchangeRate rate(CurrencyDescriptor c, LocalDate d, String r) {
        return new ExchangeRate(c, d, d, new BigDecimal(r));
    }

    private static ExchangeRate rate(CurrencyDescriptor c, LocalDate record, LocalDate effective, String r) {
        return new ExchangeRate(c, record, effective, new BigDecimal(r));
    }

    @Nested
    @DisplayName("Boundary table (AC-014..AC-020 + AC-018b + AC-019b)")
    class BoundaryTable {

        @Test
        @DisplayName("AC-014 — exact-date rate exists → that rate is selected")
        void exactDateRateSelected() {
            LocalDate txDate = LocalDate.of(2026, 4, 1);
            ExchangeRate exact = rate(CAD, txDate, "1.393000");
            Optional<ExchangeRate> result =
                    RateSelectionPolicy.selectEligibleRate(txDate, CAD, List.of(exact));
            assertThat(result).hasValue(exact);
        }

        @Test
        @DisplayName("AC-015 — no exact-date; most recent within 6 months is selected")
        void mostRecentWithinSixMonths() {
            LocalDate txDate = LocalDate.of(2026, 4, 15);
            ExchangeRate older = rate(CAD, LocalDate.of(2025, 12, 31), "1.369000");
            ExchangeRate recent = rate(CAD, LocalDate.of(2026, 3, 31), "1.393000");
            Optional<ExchangeRate> result =
                    RateSelectionPolicy.selectEligibleRate(txDate, CAD, List.of(older, recent));
            assertThat(result).hasValue(recent);
        }

        @Test
        @DisplayName("AC-016 — rate on the purchase date is preferred over an older one")
        void rateOnPurchaseDatePreferred() {
            LocalDate txDate = LocalDate.of(2026, 4, 15);
            ExchangeRate onDay = rate(CAD, txDate, "1.400000");
            ExchangeRate older = rate(CAD, LocalDate.of(2026, 3, 31), "1.393000");
            Optional<ExchangeRate> result =
                    RateSelectionPolicy.selectEligibleRate(txDate, CAD, List.of(onDay, older));
            assertThat(result).hasValue(onDay);
        }

        @Test
        @DisplayName("AC-017 — 6 months minus 1 day before — eligible")
        void sixMonthsMinusOneDayEligible() {
            // From acceptance-criteria.md AC-017: txDate=2026-04-15; only rate at recordDate=2025-10-16.
            LocalDate txDate = LocalDate.of(2026, 4, 15);
            ExchangeRate r = rate(CAD, LocalDate.of(2025, 10, 16), "1.367000");
            Optional<ExchangeRate> result =
                    RateSelectionPolicy.selectEligibleRate(txDate, CAD, List.of(r));
            assertThat(result).hasValue(r);
        }

        @Test
        @DisplayName("AC-018 — exactly 6 months before — eligible (symmetric case)")
        void exactlySixMonthsBeforeEligible() {
            // txDate=2026-04-15; rate at 2025-10-15; minusMonths(6) = 2025-10-15; inclusive ≥ check.
            LocalDate txDate = LocalDate.of(2026, 4, 15);
            ExchangeRate r = rate(CAD, LocalDate.of(2025, 10, 15), "1.367000");
            Optional<ExchangeRate> result =
                    RateSelectionPolicy.selectEligibleRate(txDate, CAD, List.of(r));
            assertThat(result).hasValue(r);
        }

        @Test
        @DisplayName("AC-018b — EOM-clamp boundary, eligible (txDate=2026-08-31; rate at 2026-02-28)")
        void eomClampBoundaryEligible() {
            // Java's LocalDate.minusMonths(6) applied to 2026-08-31 yields 2026-02-28 (EOM clamp).
            // A rate at recordDate=2026-02-28 sits exactly on the clamped lower bound and is eligible.
            LocalDate txDate = LocalDate.of(2026, 8, 31);
            ExchangeRate r = rate(CAD, LocalDate.of(2026, 2, 28), "1.380000");
            Optional<ExchangeRate> result =
                    RateSelectionPolicy.selectEligibleRate(txDate, CAD, List.of(r));
            assertThat(result).hasValue(r);
        }

        @Test
        @DisplayName("AC-019 — just outside 6 months — ineligible")
        void justOutsideSixMonthsIneligible() {
            LocalDate txDate = LocalDate.of(2026, 4, 15);
            ExchangeRate r = rate(CAD, LocalDate.of(2025, 10, 14), "1.367000");
            Optional<ExchangeRate> result =
                    RateSelectionPolicy.selectEligibleRate(txDate, CAD, List.of(r));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("AC-019b — EOM-clamp boundary, ineligible (txDate=2026-08-31; rate at 2026-02-27)")
        void eomClampBoundaryIneligible() {
            // One day below the clamped lower bound (2026-02-28) is ineligible.
            LocalDate txDate = LocalDate.of(2026, 8, 31);
            ExchangeRate r = rate(CAD, LocalDate.of(2026, 2, 27), "1.380000");
            Optional<ExchangeRate> result =
                    RateSelectionPolicy.selectEligibleRate(txDate, CAD, List.of(r));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("AC-020 — no rate at all → Optional.empty()")
        void noRateAtAll() {
            LocalDate txDate = LocalDate.of(2026, 4, 15);
            Optional<ExchangeRate> result =
                    RateSelectionPolicy.selectEligibleRate(txDate, CAD, List.of());
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Currency filtering and tie-break")
    class FilteringAndTieBreak {

        @Test
        @DisplayName("filters out rates for other currencies")
        void filtersOtherCurrencies() {
            LocalDate txDate = LocalDate.of(2026, 4, 15);
            ExchangeRate cad = rate(CAD, LocalDate.of(2026, 3, 31), "1.393000");
            ExchangeRate eur = rate(EUR, LocalDate.of(2026, 4, 1),  "0.870000");
            Optional<ExchangeRate> result =
                    RateSelectionPolicy.selectEligibleRate(txDate, CAD, List.of(cad, eur));
            assertThat(result).hasValue(cad);
        }

        @Test
        @DisplayName("filters out rates whose recordDate is AFTER the transactionDate")
        void filtersFutureRecordDates() {
            LocalDate txDate = LocalDate.of(2026, 4, 15);
            ExchangeRate future = rate(CAD, LocalDate.of(2026, 6, 30), "1.401000");
            ExchangeRate eligible = rate(CAD, LocalDate.of(2026, 3, 31), "1.393000");
            Optional<ExchangeRate> result =
                    RateSelectionPolicy.selectEligibleRate(txDate, CAD, List.of(future, eligible));
            assertThat(result).hasValue(eligible);
        }

        @Test
        @DisplayName("tie-break on same recordDate: max effectiveDate wins (OQ-002 closure)")
        void tieBreakOnEffectiveDate() {
            LocalDate txDate = LocalDate.of(2026, 4, 15);
            LocalDate recordDate = LocalDate.of(2026, 3, 31);
            // Two versions of the same record_date: the revision with the later effective_date wins.
            ExchangeRate original = rate(CAD, recordDate, recordDate,              "1.393000");
            ExchangeRate revision = rate(CAD, recordDate, recordDate.plusDays(2),  "1.395000");
            Optional<ExchangeRate> result =
                    RateSelectionPolicy.selectEligibleRate(txDate, CAD, List.of(original, revision));
            assertThat(result).hasValue(revision);
        }

        @Test
        @DisplayName("returns the rate with max recordDate when several are in window")
        void maxRecordDateInWindow() {
            LocalDate txDate = LocalDate.of(2026, 6, 30);
            ExchangeRate q1 = rate(CAD, LocalDate.of(2026, 3, 31), "1.393000");
            ExchangeRate q2 = rate(CAD, LocalDate.of(2026, 6, 30), "1.401000");
            ExchangeRate qOld = rate(CAD, LocalDate.of(2025, 12, 31), "1.369000");
            Optional<ExchangeRate> result =
                    RateSelectionPolicy.selectEligibleRate(txDate, CAD, List.of(q1, q2, qOld));
            assertThat(result).hasValue(q2);
        }
    }

    @Nested
    @DisplayName("Null safety")
    class NullSafety {

        @Test
        @DisplayName("rejects null transactionDate")
        void rejectsNullDate() {
            assertThatThrownBy(() ->
                    RateSelectionPolicy.selectEligibleRate(null, CAD, List.of()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null currency")
        void rejectsNullCurrency() {
            assertThatThrownBy(() ->
                    RateSelectionPolicy.selectEligibleRate(LocalDate.now(), null, List.of()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null candidates collection")
        void rejectsNullCandidates() {
            assertThatThrownBy(() ->
                    RateSelectionPolicy.selectEligibleRate(LocalDate.now(), CAD, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
