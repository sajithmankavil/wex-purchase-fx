package com.example.purchaseconversion.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EligibilityPolicyTest {

    @Nested
    @DisplayName("Full 3x3 boundary table (eligibility-endpoint-spec.md §3.2)")
    class BoundaryTable {

        @ParameterizedTest(name = "cardholder={0}, minimum={1} -> eligible={2}")
        @CsvSource({
                // cardholderTier, minimumTier, expectedEligible
                "PLATINUM,  PLATINUM,  true",   // exact match, lowest tier
                "PLATINUM,  SIGNATURE, false",  // below minimum
                "PLATINUM,  INFINITE,  false",  // well below minimum
                "SIGNATURE, PLATINUM,  true",   // above minimum
                "SIGNATURE, SIGNATURE, true",   // exact match, inclusive boundary
                "SIGNATURE, INFINITE,  false",  // below minimum
                "INFINITE,  PLATINUM,  true",   // well above minimum
                "INFINITE,  SIGNATURE, true",   // above minimum
                "INFINITE,  INFINITE,  true"    // exact match, highest tier
        })
        void boundaryTable(CardTier cardholderTier, CardTier minimumTier, boolean expectedEligible) {
            assertThat(EligibilityPolicy.isEligible(cardholderTier, minimumTier))
                    .isEqualTo(expectedEligible);
        }
    }

    @Test
    @DisplayName("rejects null cardholderTier")
    void rejectsNullCardholderTier() {
        assertThatThrownBy(() -> EligibilityPolicy.isEligible(null, CardTier.PLATINUM))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects null minimumTier")
    void rejectsNullMinimumTier() {
        assertThatThrownBy(() -> EligibilityPolicy.isEligible(CardTier.PLATINUM, null))
                .isInstanceOf(NullPointerException.class);
    }
}
