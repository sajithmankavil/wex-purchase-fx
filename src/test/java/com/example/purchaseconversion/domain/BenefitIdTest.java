package com.example.purchaseconversion.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BenefitIdTest {

    @Test
    @DisplayName("happy path: valid value constructs a BenefitId")
    void happyPath() {
        assertThat(BenefitId.of("BEN-1042").value()).isEqualTo("BEN-1042");
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
        assertThatThrownBy(() -> new BenefitId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects blank")
    void rejectsBlank() {
        assertThatThrownBy(() -> new BenefitId("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("accepts exactly 64 characters, rejects 65")
    void lengthBoundary() {
        String exactly64 = "B".repeat(64);
        assertThat(BenefitId.of(exactly64).value()).hasSize(64);

        String tooLong = "B".repeat(65);
        assertThatThrownBy(() -> new BenefitId(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("equality is value-based")
    void valueEquality() {
        assertThat(BenefitId.of("BEN-1042")).isEqualTo(BenefitId.of("BEN-1042"));
    }
}
