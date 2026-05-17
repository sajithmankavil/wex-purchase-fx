package com.example.purchaseconversion.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A strictly positive USD monetary amount at scale 2 (cents precision).
 *
 * <p>Per ADR-0001 D-2 / D-6 and NFR-030, money is BigDecimal-only — never {@code double}
 * or {@code float}. {@code multiply(rate)} performs the Treasury-rate conversion at full
 * BigDecimal precision and rounds the final result to scale 2 using {@link RoundingMode#HALF_UP}
 * (D-6; AC-025; AC-026).
 *
 * <p>Constructor invariants:
 * <ul>
 *   <li>{@code amount} is non-null.</li>
 *   <li>{@code amount.signum() > 0} — strictly positive (FR-001).</li>
 *   <li>{@code amount.scale() <= 2} — admits whole-dollar and dime inputs;
 *       rejects sub-cent precision. Scale &lt; 2 is padded with {@link BigDecimal#setScale(int)}.</li>
 * </ul>
 */
public final class Money {

    /** Maximum permitted scale on inbound BigDecimal; cents precision. */
    public static final int CENTS_SCALE = 2;

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount;
    }

    /**
     * Constructs a {@code Money} from a non-null, strictly positive BigDecimal with scale ≤ 2.
     *
     * @throws NullPointerException     if {@code amount} is null
     * @throws IllegalArgumentException if amount is non-positive or scale &gt; 2
     */
    public static Money of(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be strictly positive; got " + amount);
        }
        if (amount.scale() > CENTS_SCALE) {
            throw new IllegalArgumentException(
                    "amount scale must be <= " + CENTS_SCALE + "; got " + amount.scale() + " for " + amount);
        }
        // Pad scale-0 or scale-1 to scale 2 (no rounding, exact representation).
        BigDecimal normalised = amount.setScale(CENTS_SCALE, RoundingMode.UNNECESSARY);
        return new Money(normalised);
    }

    /**
     * Parses a {@code Money} from a decimal-string literal.
     *
     * @throws NullPointerException     if {@code amount} is null
     * @throws NumberFormatException    if {@code amount} is not a valid decimal literal
     * @throws IllegalArgumentException if amount is non-positive or scale &gt; 2
     */
    public static Money of(String amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        return of(new BigDecimal(amount));
    }

    /**
     * The underlying scale-2 BigDecimal value.
     */
    public BigDecimal value() {
        return amount;
    }

    /**
     * Multiplies this amount by a Treasury exchange rate (foreign-units per USD) and rounds
     * the result to scale 2 using HALF_UP (D-4; AC-025).
     *
     * <p>BigDecimal multiplication is exact at any scale, so the intermediate product preserves
     * full precision (scale = {@code amount.scale()} + {@code rate.scale()}). The final
     * {@code setScale(2, HALF_UP)} is the only rounding operation.
     *
     * @param rate non-null, strictly positive exchange rate (caller's responsibility — typically
     *             validated by {@link ExchangeRate})
     * @return a new {@code Money} representing the converted amount
     * @throws NullPointerException     if {@code rate} is null
     * @throws IllegalArgumentException if {@code rate.signum() <= 0} or the rounded result is non-positive
     */
    public Money multiply(BigDecimal rate) {
        Objects.requireNonNull(rate, "rate must not be null");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("rate must be strictly positive; got " + rate);
        }
        BigDecimal product = amount.multiply(rate);
        BigDecimal rounded = product.setScale(CENTS_SCALE, RoundingMode.HALF_UP);
        return Money.of(rounded);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
