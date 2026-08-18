package com.example.purchaseconversion.api.controller;

import com.example.purchaseconversion.observability.EligibilityAuditLogger;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Base for {@code @WebMvcTest} slices, mocking beans that transitively load into
 * EVERY such slice in this repo regardless of which controller is under test —
 * {@code @WebMvcTest} auto-includes {@code Filter}, {@code HandlerInterceptor}, and
 * {@code WebMvcConfigurer} beans by default (a Spring Boot documented behavior, not
 * an oversight), so their constructor dependencies must be satisfiable everywhere.
 *
 * <p>Extend this instead of re-declaring the same {@code @MockBean}s per test class —
 * it centralises the fix so a future global filter/interceptor only needs updating
 * here, not in every slice test that happens to exist at the time. This came out of
 * a review finding: adding {@code EligibilityAuditInterceptor} silently broke
 * {@code PurchaseControllerWebMvcTest} (an entirely unrelated controller) until its
 * transitive {@code EligibilityAuditLogger} dependency was mocked there too.
 *
 * <ul>
 *   <li>{@code RateLimiterRegistry} — required by {@code WexRateLimiterFilter}.</li>
 *   <li>{@code EligibilityAuditLogger} — required by {@code EligibilityAuditInterceptor}.</li>
 * </ul>
 */
abstract class AbstractWebMvcTestSupport {

    @MockBean protected RateLimiterRegistry rateLimiterRegistry;
    @MockBean protected EligibilityAuditLogger eligibilityAuditLogger;
}
