package com.example.purchaseconversion.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyDescriptorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Canada-Dollar",
            "Japan-Yen",
            "UK-Pound Sterling",
            "Euro Zone-Euro",     // Phase-3 prototype P-1: the space-bearing canonical form
            "Mexico-Peso"
    })
    @DisplayName("accepts canonical Treasury descriptors verbatim, including the space-bearing Eurozone form")
    void acceptsCanonicalDescriptors(String value) {
        CurrencyDescriptor d = CurrencyDescriptor.of(value);
        assertThat(d.value()).isEqualTo(value);
        assertThat(d.toString()).isEqualTo(value);
    }

    @Test
    @DisplayName("preserves the embedded space in 'Euro Zone-Euro' (no normalisation)")
    void preservesEmbeddedSpace() {
        CurrencyDescriptor d = CurrencyDescriptor.of("Euro Zone-Euro");
        assertThat(d.value()).contains(" ");
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
        assertThatThrownBy(() -> CurrencyDescriptor.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("rejects blank values")
    void rejectsBlank(String value) {
        assertThatThrownBy(() -> CurrencyDescriptor.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("accepts exactly 64 characters at the boundary")
    void acceptsExactly64Chars() {
        String exactly64 = "x".repeat(64);
        assertThat(CurrencyDescriptor.of(exactly64).value()).hasSize(64);
    }

    @Test
    @DisplayName("rejects 65 characters")
    void rejects65Chars() {
        String over = "x".repeat(65);
        assertThatThrownBy(() -> CurrencyDescriptor.of(over))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64");
    }

    @Test
    @DisplayName("equality is by exact string value (case-sensitive)")
    void equalityIsCaseSensitive() {
        CurrencyDescriptor canonical = CurrencyDescriptor.of("Canada-Dollar");
        CurrencyDescriptor differentCase = CurrencyDescriptor.of("canada-dollar");
        // Domain does not case-fold; alias resolution belongs to the application layer.
        assertThat(canonical).isNotEqualTo(differentCase);
    }
}
