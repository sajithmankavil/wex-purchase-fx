package com.example.purchaseconversion.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardTierTest {

    @Test
    @DisplayName("declared ordering is PLATINUM < SIGNATURE < INFINITE (spec §3.1)")
    void declaredOrdering() {
        assertThat(CardTier.PLATINUM.ordinal()).isLessThan(CardTier.SIGNATURE.ordinal());
        assertThat(CardTier.SIGNATURE.ordinal()).isLessThan(CardTier.INFINITE.ordinal());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATINUM", "SIGNATURE", "INFINITE"})
    @DisplayName("parses each canonical wire value")
    void parsesCanonicalValues(String raw) {
        assertThat(CardTier.parse(raw)).hasValue(CardTier.valueOf(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GOLD", "platinum", "Platinum", "", " PLATINUM", "PLATINUM "})
    @DisplayName("returns empty for anything not an exact case-sensitive match")
    void rejectsUnrecognized(String raw) {
        assertThat(CardTier.parse(raw)).isEmpty();
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
        assertThatThrownBy(() -> CardTier.parse(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("meetsMinimum is inclusive at the exact boundary")
    void meetsMinimumInclusiveBoundary() {
        assertThat(CardTier.SIGNATURE.meetsMinimum(CardTier.SIGNATURE)).isTrue();
    }

    @Test
    @DisplayName("meetsMinimum true when above, false when below")
    void meetsMinimumAboveAndBelow() {
        assertThat(CardTier.INFINITE.meetsMinimum(CardTier.PLATINUM)).isTrue();
        assertThat(CardTier.PLATINUM.meetsMinimum(CardTier.INFINITE)).isFalse();
    }

    @Test
    @DisplayName("meetsMinimum rejects null")
    void meetsMinimumRejectsNull() {
        assertThatThrownBy(() -> CardTier.PLATINUM.meetsMinimum(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Optional.of(...) form matches an exact single-argument value")
    void singleValueParse() {
        Optional<CardTier> result = CardTier.parse("INFINITE");
        assertThat(result).contains(CardTier.INFINITE);
    }
}
