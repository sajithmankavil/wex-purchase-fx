package com.example.purchaseconversion.infrastructure.health;

import com.example.purchaseconversion.infrastructure.cache.BenefitEligibilityCacheAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenefitEligibilityHealthIndicatorTest {

    @Mock
    private BenefitEligibilityCacheAdapter cache;

    @Test
    @DisplayName("reports DOWN before the cache's first load succeeds (spec §4.3 fail-closed cold start)")
    void downBeforeLoaded() {
        when(cache.isLoaded()).thenReturn(false);
        BenefitEligibilityHealthIndicator indicator = new BenefitEligibilityHealthIndicator(cache);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("reports UP once loaded")
    void upOnceLoaded() {
        when(cache.isLoaded()).thenReturn(true);
        BenefitEligibilityHealthIndicator indicator = new BenefitEligibilityHealthIndicator(cache);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }
}
