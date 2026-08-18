package com.example.purchaseconversion.application.eligibility;

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
    @DisplayName("Eligible when cardholder tier meets the benefit's minimum")
    void eligibleWhenTierMeetsMinimum() {
        when(cache.minimumTierFor(BENEFIT_ID)).thenReturn(Optional.of(CardTier.SIGNATURE));

        EligibilityResult result = service.check(BENEFIT_ID, CardTier.INFINITE);

        assertThat(result).isEqualTo(new EligibilityResult.Eligible(BENEFIT_ID, CardTier.INFINITE));
    }

    @Test
    @DisplayName("NotEligible, carrying the minimumTier, when cardholder tier is below it")
    void notEligibleWhenTierBelowMinimum() {
        when(cache.minimumTierFor(BENEFIT_ID)).thenReturn(Optional.of(CardTier.INFINITE));

        EligibilityResult result = service.check(BENEFIT_ID, CardTier.PLATINUM);

        assertThat(result).isEqualTo(
                new EligibilityResult.NotEligible(BENEFIT_ID, CardTier.PLATINUM, CardTier.INFINITE));
    }

    @Test
    @DisplayName("Eligible at the exact boundary (inclusive)")
    void eligibleAtExactBoundary() {
        when(cache.minimumTierFor(BENEFIT_ID)).thenReturn(Optional.of(CardTier.SIGNATURE));

        EligibilityResult result = service.check(BENEFIT_ID, CardTier.SIGNATURE);

        assertThat(result).isEqualTo(new EligibilityResult.Eligible(BENEFIT_ID, CardTier.SIGNATURE));
    }

    @Test
    @DisplayName("NotFound when the benefit is unknown to the cache (spec §3.3) — no exception thrown")
    void unknownBenefitReturnsNotFound() {
        when(cache.minimumTierFor(BENEFIT_ID)).thenReturn(Optional.empty());

        EligibilityResult result = service.check(BENEFIT_ID, CardTier.PLATINUM);

        assertThat(result).isEqualTo(new EligibilityResult.NotFound(BENEFIT_ID));
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
