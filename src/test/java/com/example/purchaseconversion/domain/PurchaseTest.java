package com.example.purchaseconversion.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseTest {

    private static final PurchaseId ID = PurchaseId.next();
    private static final LocalDate TX_DATE = LocalDate.of(2026, 4, 1);
    private static final Money AMOUNT = Money.of("123.45");

    @Test
    @DisplayName("happy path: valid fields construct a Purchase")
    void happyPath() {
        Purchase p = new Purchase(ID, "Office supplies", TX_DATE, AMOUNT);
        assertThat(p.id()).isEqualTo(ID);
        assertThat(p.description()).isEqualTo("Office supplies");
        assertThat(p.transactionDate()).isEqualTo(TX_DATE);
        assertThat(p.amountUsd()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("accepts a description at exactly 50 characters (AC-002)")
    void acceptsExactly50CharDescription() {
        String exactly50 = "x".repeat(50);
        Purchase p = new Purchase(ID, exactly50, TX_DATE, AMOUNT);
        assertThat(p.description()).hasSize(50);
    }

    @Test
    @DisplayName("rejects a description of 51 characters (AC-003)")
    void rejects51CharDescription() {
        String over = "x".repeat(51);
        assertThatThrownBy(() -> new Purchase(ID, over, TX_DATE, AMOUNT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("rejects a blank description (AC-004)")
    void rejectsBlankDescription(String blank) {
        assertThatThrownBy(() -> new Purchase(ID, blank, TX_DATE, AMOUNT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("rejects null fields")
    void rejectsNullFields() {
        assertThatThrownBy(() -> new Purchase(null, "x", TX_DATE, AMOUNT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Purchase(ID, null, TX_DATE, AMOUNT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Purchase(ID, "x", null, AMOUNT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Purchase(ID, "x", TX_DATE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("equality is value-based via the record contract")
    void valueEquality() {
        Purchase a = new Purchase(ID, "Office supplies", TX_DATE, AMOUNT);
        Purchase b = new Purchase(ID, "Office supplies", TX_DATE, AMOUNT);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
