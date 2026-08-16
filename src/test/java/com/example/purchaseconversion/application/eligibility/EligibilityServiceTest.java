package com.example.purchaseconversion.application.eligibility;

import com.example.purchaseconversion.application.exception.BenefitNotFoundException;
import com.example.purchaseconversion.application.port.out.BenefitEligibilityCachePort;
import com.example.purchaseconversion.domain.BenefitId;
import com.example.purchaseconversion.domain.CardTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EligibilityServiceTest {

    private static final BenefitId BENEFIT_ID = BenefitId.of("BEN-1042");

    @Mock
    private BenefitEligibilityCachePort cache;

    private EligibilityService service;

    @BeforeEach
    void setUp() {
        service = new EligibilityService(cache);
    }

    @Test
    @DisplayName("eligible when cardholder tier meets the benefit's minimum")
    void eligibleWhenTierMeetsMinimum() {
        when(cache.minimumTierFor(BENEFIT_ID)).thenReturn(Optional.of(CardTier.SIGNATURE));

        assertThat(service.check(BENEFIT_ID, CardTier.INFINITE)).isTrue();
    }

    @Test
    @DisplayName("not eligible when cardholder tier is below the benefit's minimum")
    void notEligibleWhenTierBelowMinimum() {
        when(cache.minimumTierFor(BENEFIT_ID)).thenReturn(Optional.of(CardTier.INFINITE));

        assertThat(service.check(BENEFIT_ID, CardTier.PLATINUM)).isFalse();
    }

    @Test
    @DisplayName("eligible at the exact boundary (inclusive)")
    void eligibleAtExactBoundary() {
        when(cache.minimumTierFor(BENEFIT_ID)).thenReturn(Optional.of(CardTier.SIGNATURE));

        assertThat(service.check(BENEFIT_ID, CardTier.SIGNATURE)).isTrue();
    }

    @Test
    @DisplayName("unknown benefit raises BenefitNotFoundException (spec §3.3)")
    void unknownBenefitThrows() {
        when(cache.minimumTierFor(BENEFIT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.check(BENEFIT_ID, CardTier.PLATINUM))
                .isInstanceOf(BenefitNotFoundException.class)
                .satisfies(t -> assertThat(((BenefitNotFoundException) t).getBenefitId()).isEqualTo(BENEFIT_ID));
    }

    @Test
    @DisplayName("rejects null benefitId")
    void rejectsNullBenefitId() {
        assertThatThrownBy(() -> service.check(null, CardTier.PLATINUM))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects null tier")
    void rejectsNullTier() {
        assertThatThrownBy(() -> service.check(BENEFIT_ID, null))
                .isInstanceOf(NullPointerException.class);
    }
}
