package com.example.purchaseconversion.api.controller;

import com.example.purchaseconversion.api.advice.ContentGuard;
import com.example.purchaseconversion.api.advice.ContentGuardAdvice;
import com.example.purchaseconversion.api.advice.ProblemDetailExceptionHandler;
import com.example.purchaseconversion.application.exception.BenefitNotFoundException;
import com.example.purchaseconversion.application.port.in.CheckEligibilityUseCase;
import com.example.purchaseconversion.domain.BenefitId;
import com.example.purchaseconversion.domain.CardTier;
import com.example.purchaseconversion.observability.DescriptionHasher;
import com.example.purchaseconversion.observability.EligibilityAuditLogger;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvc slice tests for {@link BenefitEligibilityController}
 * (eligibility-endpoint-spec.md §2, §7).
 */
@WebMvcTest(BenefitEligibilityController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ContentGuard.class, ContentGuardAdvice.class, ProblemDetailExceptionHandler.class,
        BenefitEligibilityControllerWebMvcTest.HasherConfig.class})
class BenefitEligibilityControllerWebMvcTest {

    @Autowired private MockMvc mvc;

    @MockBean private CheckEligibilityUseCase checkEligibility;
    @MockBean private EligibilityAuditLogger auditLogger;
    // Satisfies WexRateLimiterFilter's constructor (component-scanned by the slice but
    // not applied — addFilters=false above disables the chain).
    @MockBean private RateLimiterRegistry rateLimiterRegistry;

    @Test
    @DisplayName("200 — eligible: true")
    void eligibleTrue() throws Exception {
        when(checkEligibility.check(BenefitId.of("BEN-1042"), CardTier.SIGNATURE)).thenReturn(true);

        mvc.perform(get("/api/v1/benefits/{benefitId}/eligibility", "BEN-1042").param("tier", "SIGNATURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.benefitId").value("BEN-1042"))
                .andExpect(jsonPath("$.tier").value("SIGNATURE"))
                .andExpect(jsonPath("$.eligible").value(true));
    }

    @Test
    @DisplayName("200 — eligible: false")
    void eligibleFalse() throws Exception {
        when(checkEligibility.check(BenefitId.of("BEN-1042"), CardTier.PLATINUM)).thenReturn(false);

        mvc.perform(get("/api/v1/benefits/{benefitId}/eligibility", "BEN-1042").param("tier", "PLATINUM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false));
    }

    @Test
    @DisplayName("200 — logs exactly one audit line per call")
    void logsAuditLine() throws Exception {
        when(checkEligibility.check(any(BenefitId.class), any(CardTier.class))).thenReturn(true);

        mvc.perform(get("/api/v1/benefits/{benefitId}/eligibility", "BEN-1042").param("tier", "SIGNATURE"))
                .andExpect(status().isOk());

        verify(auditLogger).logCheck(anyString(), anyString(), anyBoolean(), anyLong());
    }

    @Test
    @DisplayName("400 — unrecognized tier → INVALID_TIER")
    void invalidTier() throws Exception {
        mvc.perform(get("/api/v1/benefits/{benefitId}/eligibility", "BEN-1042").param("tier", "GOLD"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIER"))
                .andExpect(jsonPath("$.details.tier").value("GOLD"));
    }

    @Test
    @DisplayName("400 — missing tier query param → MISSING_TIER")
    void missingTier() throws Exception {
        mvc.perform(get("/api/v1/benefits/{benefitId}/eligibility", "BEN-1042"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("MISSING_TIER"));
    }

    @Test
    @DisplayName("404 — unknown benefit → BENEFIT_NOT_FOUND")
    void benefitNotFound() throws Exception {
        when(checkEligibility.check(BenefitId.of("BEN-9999"), CardTier.SIGNATURE))
                .thenThrow(new BenefitNotFoundException(BenefitId.of("BEN-9999")));

        mvc.perform(get("/api/v1/benefits/{benefitId}/eligibility", "BEN-9999").param("tier", "SIGNATURE"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("BENEFIT_NOT_FOUND"))
                .andExpect(jsonPath("$.details.benefitId").value("BEN-9999"));
    }

    @TestConfiguration
    static class HasherConfig {
        @Bean DescriptionHasher hasher() { return new DescriptionHasher("test", "", "v1"); }
    }
}
