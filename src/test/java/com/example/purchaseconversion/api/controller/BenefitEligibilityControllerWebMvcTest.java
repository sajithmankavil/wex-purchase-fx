package com.example.purchaseconversion.api.controller;

import com.example.purchaseconversion.api.advice.ContentGuard;
import com.example.purchaseconversion.api.advice.ContentGuardAdvice;
import com.example.purchaseconversion.api.advice.ProblemDetailExceptionHandler;
import com.example.purchaseconversion.api.interceptor.EligibilityAuditInterceptor;
import com.example.purchaseconversion.application.eligibility.EligibilityResult;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvc slice tests for {@link BenefitEligibilityController}
 * (eligibility-endpoint-spec.md §2, §7).
 *
 * <p>{@code @WebMvcTest} auto-includes {@code HandlerInterceptor} and
 * {@code WebMvcConfigurer} beans by default, so {@code EligibilityAuditInterceptor}
 * (registered by {@code WebMvcConfig} for {@code /api/v1/benefits/**}) genuinely runs
 * in this slice — {@link #eligibleTrue()} asserts the full path end-to-end via
 * {@link MvcResult#getRequest()} plus a {@code verify(...)} on the mocked
 * {@code EligibilityAuditLogger}. The interceptor's own edge cases (best-effort
 * failure handling, no-line-on-error-paths) are covered in isolation by
 * {@code EligibilityAuditInterceptorTest}.
 */
@WebMvcTest(BenefitEligibilityController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ContentGuard.class, ContentGuardAdvice.class, ProblemDetailExceptionHandler.class,
        BenefitEligibilityControllerWebMvcTest.HasherConfig.class})
class BenefitEligibilityControllerWebMvcTest {

    @Autowired private MockMvc mvc;

    @MockBean private CheckEligibilityUseCase checkEligibility;
    // Satisfies WexRateLimiterFilter's constructor (component-scanned by the slice but
    // not applied — addFilters=false above disables the chain).
    @MockBean private RateLimiterRegistry rateLimiterRegistry;
    // Satisfies EligibilityAuditInterceptor's constructor — @WebMvcTest auto-includes
    // HandlerInterceptor beans, so this is required even though the controller itself
    // no longer depends on the logger directly (moved to the interceptor, see class javadoc).
    @MockBean private EligibilityAuditLogger eligibilityAuditLogger;

    @Test
    @DisplayName("200 — Eligible → eligible:true, and the full audit path fires end-to-end")
    void eligibleTrue() throws Exception {
        when(checkEligibility.check(BenefitId.of("BEN-1042"), CardTier.SIGNATURE))
                .thenReturn(new EligibilityResult.Eligible(BenefitId.of("BEN-1042"), CardTier.SIGNATURE));

        MvcResult result = mvc.perform(get("/api/v1/benefits/{benefitId}/eligibility", "BEN-1042")
                        .param("tier", "SIGNATURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.benefitId").value("BEN-1042"))
                .andExpect(jsonPath("$.tier").value("SIGNATURE"))
                .andExpect(jsonPath("$.eligible").value(true))
                .andReturn();

        // @WebMvcTest auto-includes WebMvcConfigurer beans, so EligibilityAuditInterceptor
        // genuinely runs in this slice (registered via WebMvcConfig for this path) — this
        // proves the controller's stamped attributes and the interceptor's afterCompletion
        // wiring actually connect, not just that the controller sets attributes in isolation.
        assertThat(result.getRequest().getAttribute(EligibilityAuditInterceptor.ATTR_BENEFIT_ID))
                .isEqualTo("BEN-1042");
        assertThat(result.getRequest().getAttribute(EligibilityAuditInterceptor.ATTR_TIER))
                .isEqualTo("SIGNATURE");
        assertThat(result.getRequest().getAttribute(EligibilityAuditInterceptor.ATTR_ELIGIBLE))
                .isEqualTo(true);
        assertThat(result.getRequest().getAttribute(EligibilityAuditInterceptor.ATTR_START_NANOS))
                .isNotNull();
        verify(eligibilityAuditLogger).logCheck(
                org.mockito.ArgumentMatchers.eq("BEN-1042"),
                org.mockito.ArgumentMatchers.eq("SIGNATURE"),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("200 — NotEligible → eligible:false")
    void eligibleFalse() throws Exception {
        when(checkEligibility.check(BenefitId.of("BEN-1042"), CardTier.PLATINUM))
                .thenReturn(new EligibilityResult.NotEligible(
                        BenefitId.of("BEN-1042"), CardTier.PLATINUM, CardTier.SIGNATURE));

        mvc.perform(get("/api/v1/benefits/{benefitId}/eligibility", "BEN-1042").param("tier", "PLATINUM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false));
    }

    @Test
    @DisplayName("400 — unrecognized tier → INVALID_TIER; no audit attributes stamped")
    void invalidTier() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/benefits/{benefitId}/eligibility", "BEN-1042")
                        .param("tier", "GOLD"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIER"))
                .andExpect(jsonPath("$.details.tier").value("GOLD"))
                .andReturn();

        assertThat(result.getRequest().getAttribute(EligibilityAuditInterceptor.ATTR_BENEFIT_ID)).isNull();
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
    @DisplayName("404 — NotFound → BENEFIT_NOT_FOUND; no audit attributes stamped")
    void benefitNotFound() throws Exception {
        when(checkEligibility.check(any(BenefitId.class), any(CardTier.class)))
                .thenReturn(new EligibilityResult.NotFound(BenefitId.of("BEN-9999")));

        MvcResult result = mvc.perform(get("/api/v1/benefits/{benefitId}/eligibility", "BEN-9999")
                        .param("tier", "SIGNATURE"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("BENEFIT_NOT_FOUND"))
                .andExpect(jsonPath("$.details.benefitId").value("BEN-9999"))
                .andReturn();

        assertThat(result.getRequest().getAttribute(EligibilityAuditInterceptor.ATTR_ELIGIBLE)).isNull();
    }

    @TestConfiguration
    static class HasherConfig {
        @Bean DescriptionHasher hasher() { return new DescriptionHasher("test", "", "v1"); }
    }
}
