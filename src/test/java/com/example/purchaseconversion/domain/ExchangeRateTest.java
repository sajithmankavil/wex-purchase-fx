package com.example.purchaseconversion.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeRateTest {

    private static final CurrencyDescriptor CAD = CurrencyDescriptor.of("Canada-Dollar");
    private static final LocalDate RECORD_DATE = LocalDate.of(2026, 3, 31);
    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2026, 3, 31);

    @Test
    @DisplayName("happy path: valid fields construct an ExchangeRate")
    void happyPath() {
        ExchangeRate r = new ExchangeRate(CAD, RECORD_DATE, EFFECTIVE_DATE, new BigDecimal("1.393000"));
        assertThat(r.currency()).isEqualTo(CAD);
        assertThat(r.rate()).isEqualByComparingTo("1.393000");
    }

    @Test
    @DisplayName("accepts effectiveDate AFTER recordDate (revision case; AC-026b)")
    void acceptsEffectiveDateAfterRecordDate() {
        LocalDate later = RECORD_DATE.plusDays(2);
        ExchangeRate r = new ExchangeRate(CAD, RECORD_DATE, later, new BigDecimal("1.401000"));
        assertThat(r.effectiveDate()).isAfter(r.recordDate());
    }

    @Test
    @DisplayName("accepts effectiveDate BEFORE recordDate (Phase-6 G4-P1-2 softened constraint)")
    void acceptsEffectiveDateBeforeRecordDate() {
        // The Phase-4/6 grill loosened the strict >= constraint; only non-null is enforced.
        LocalDate earlier = RECORD_DATE.minusDays(1);
        ExchangeRate r = new ExchangeRate(CAD, RECORD_DATE, earlier, new BigDecimal("1.350000"));
        assertThat(r).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.000001", "-1", "-1000"})
    @DisplayName("rejects non-positive rate (AC-024b)")
    void rejectsNonPositiveRate(String rateValue) {
        assertThatThrownBy(() ->
                new ExchangeRate(CAD, RECORD_DATE, EFFECTIVE_DATE, new BigDecimal(rateValue)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("accepts a rate at exactly the 1e30 sanity ceiling (Phase-6 G6-P1-3)")
    void acceptsAtSanityCeiling() {
        BigDecimal ceiling = new BigDecimal("1e30");
        ExchangeRate r = new ExchangeRate(CAD, RECORD_DATE, EFFECTIVE_DATE, ceiling);
        assertThat(r.rate()).isEqualByComparingTo(ceiling);
    }

    @Test
    @DisplayName("rejects a rate above the 1e30 sanity ceiling")
    void rejectsAboveSanityCeiling() {
        BigDecimal over = new BigDecimal("1.1e30");
        assertThatThrownBy(() -> new ExchangeRate(CAD, RECORD_DATE, EFFECTIVE_DATE, over))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1e30");
    }

    @Test
    @DisplayName("accepts a hyperinflation-class rate (Zimbabwe peak ~1e25)")
    void acceptsHyperinflationRate() {
        BigDecimal hyperinflation = new BigDecimal("1e25");
        ExchangeRate r = new ExchangeRate(CAD, RECORD_DATE, EFFECTIVE_DATE, hyperinflation);
        assertThat(r.rate()).isEqualByComparingTo(hyperinflation);
    }

    @Test
    @DisplayName("rejects null fields")
    void rejectsNullFields() {
        assertThatThrownBy(() ->
                new ExchangeRate(null, RECORD_DATE, EFFECTIVE_DATE, new BigDecimal("1.0")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                new ExchangeRate(CAD, null, EFFECTIVE_DATE, new BigDecimal("1.0")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                new ExchangeRate(CAD, RECORD_DATE, null, new BigDecimal("1.0")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                new ExchangeRate(CAD, RECORD_DATE, EFFECTIVE_DATE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("equality is value-based via the record contract (composite key)")
    void valueEquality() {
        ExchangeRate a = new ExchangeRate(CAD, RECORD_DATE, EFFECTIVE_DATE, new BigDecimal("1.393000"));
        ExchangeRate b = new ExchangeRate(CAD, RECORD_DATE, EFFECTIVE_DATE, new BigDecimal("1.393000"));
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
