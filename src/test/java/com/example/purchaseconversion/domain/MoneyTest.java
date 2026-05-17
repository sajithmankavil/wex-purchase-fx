package com.example.purchaseconversion.domain;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Positive;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    @DisplayName("accepts a strictly positive BigDecimal at scale 2")
    void acceptsStrictlyPositiveScale2() {
        Money m = Money.of(new BigDecimal("123.45"));
        assertThat(m.value()).isEqualByComparingTo("123.45");
        assertThat(m.value().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("pads scale-0 or scale-1 inputs to scale 2")
    void padsLowerScaleToScale2() {
        assertThat(Money.of(new BigDecimal("100")).value()).isEqualByComparingTo("100.00");
        assertThat(Money.of(new BigDecimal("100.5")).value()).isEqualByComparingTo("100.50");
        assertThat(Money.of(new BigDecimal("100")).value().scale()).isEqualTo(2);
        assertThat(Money.of(new BigDecimal("100.5")).value().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("rejects scale > 2 (no silent rounding of inbound payload — AC-008)")
    void rejectsScaleGreaterThan2() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("12.345")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.01", "-100", "-100.50"})
    @DisplayName("rejects zero or negative amounts (FR-001 positive constraint)")
    void rejectsNonPositive(String value) {
        assertThatThrownBy(() -> Money.of(new BigDecimal(value)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("rejects null amount")
    void rejectsNull() {
        assertThatThrownBy(() -> Money.of((BigDecimal) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Money.of((String) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("parses a decimal string via Money.of(String)")
    void parsesDecimalString() {
        assertThat(Money.of("123.45").value()).isEqualByComparingTo("123.45");
    }

    @ParameterizedTest
    @MethodSource("halfUpCases")
    @DisplayName("multiply(rate) rounds HALF_UP to scale 2 (D-4, AC-025)")
    void multiplyHalfUp(String amount, String rate, String expected) {
        Money result = Money.of(amount).multiply(new BigDecimal(rate));
        assertThat(result.value()).isEqualByComparingTo(expected);
        assertThat(result.value().scale()).isEqualTo(2);
    }

    static Stream<Arguments> halfUpCases() {
        return Stream.of(
                // AC-025 explicit cases
                Arguments.of("1.00", "0.875",    "0.88"),
                Arguments.of("1.00", "0.865",    "0.87"),
                Arguments.of("1.00", "0.864999", "0.86"),
                // The case-study Phase-3 prototype example
                Arguments.of("123.45", "1.3700", "169.13"),
                // The Phase-3 prototype empirical CAD value at scale 6
                Arguments.of("123.45", "1.393000", "171.97"),
                // EUR / JPY anchors from the prototype
                Arguments.of("100.00", "0.870000", "87.00"),
                Arguments.of("100.00", "159.410000", "15941.00"),
                // Half-tie at exactly 0.5 — HALF_UP rounds away from zero
                Arguments.of("0.10", "0.05", "0.01")
        );
    }

    @Test
    @DisplayName("multiply preserves precision for a large amount × high-precision rate (AC-026)")
    void multiplyPreservesPrecisionForLargeAmount() {
        Money m = Money.of("99999999.99");
        BigDecimal rate = new BigDecimal("0.918237");
        Money result = m.multiply(rate);
        // Reference: 99999999.99 * 0.918237 = 91823699.99... → HALF_UP scale 2 = 91823699.08
        // We compute the expected value directly to avoid drifting if jdk rounding changes.
        BigDecimal expected = m.value().multiply(rate).setScale(2, RoundingMode.HALF_UP);
        assertThat(result.value()).isEqualByComparingTo(expected);
        assertThat(result.value().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("multiply rejects null rate")
    void multiplyRejectsNullRate() {
        assertThatThrownBy(() -> Money.of("1.00").multiply(null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "-1"})
    @DisplayName("multiply rejects non-positive rate")
    void multiplyRejectsNonPositiveRate(String rateValue) {
        assertThatThrownBy(() -> Money.of("1.00").multiply(new BigDecimal(rateValue)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("equality compares value, not representation (100.00 == 100.0)")
    void equalityIgnoresRepresentation() {
        Money a = Money.of(new BigDecimal("100"));   // padded to 100.00
        Money b = Money.of(new BigDecimal("100.00"));
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("toString returns the plain decimal string at scale 2")
    void toStringIsPlainScale2() {
        assertThat(Money.of("99.95").toString()).isEqualTo("99.95");
        assertThat(Money.of("100").toString()).isEqualTo("100.00");
    }

    // ---- Property-based: HALF_UP across signs / scales / magnitudes ----

    @Provide
    Arbitrary<BigDecimal> positiveScale2Amounts() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("9999999.99"))
                .ofScale(2)
                .filter(b -> b.signum() > 0);
    }

    @Provide
    Arbitrary<BigDecimal> positiveRatesScale6() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.000001"), new BigDecimal("1000000.000000"))
                .ofScale(6)
                .filter(b -> b.signum() > 0);
    }

    @Property
    @DisplayName("property: multiply returns Money at scale 2")
    void propertyMultiplyResultIsScale2(
            @ForAll("positiveScale2Amounts") BigDecimal amount,
            @ForAll("positiveRatesScale6") BigDecimal rate) {
        Money result = Money.of(amount).multiply(rate);
        Assertions.assertThat(result.value().scale()).isEqualTo(2);
    }

    @Property
    @DisplayName("property: multiply result equals HALF_UP-rounded exact product")
    void propertyMultiplyMatchesExactHalfUp(
            @ForAll("positiveScale2Amounts") BigDecimal amount,
            @ForAll("positiveRatesScale6") BigDecimal rate) {
        Money result = Money.of(amount).multiply(rate);
        BigDecimal exact = amount.setScale(2, RoundingMode.UNNECESSARY).multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);
        Assertions.assertThat(result.value()).isEqualByComparingTo(exact);
    }

    @Property
    @DisplayName("property: Money round-trips through value() / of(value())")
    void propertyRoundTripValueOf(@ForAll @Positive BigDecimal positive) {
        // Round-trip is meaningful only for inputs that the constructor accepts as-is.
        // Cap scale at 2 so we exercise the canonical path.
        BigDecimal scale2 = positive.setScale(2, RoundingMode.HALF_UP);
        Money m = Money.of(scale2);
        Assertions.assertThat(Money.of(m.value())).isEqualTo(m);
    }
}
