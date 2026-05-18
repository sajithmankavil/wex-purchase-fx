package com.example.purchaseconversion.api.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Servlet-layer rate-limiter (component-design.md §5; G8-P0-1).
 *
 * <p>Per the Phase-8 grill ordering ruling, rate-limiting MUST run BEFORE
 * the {@code @RestControllerAdvice} ContentGuard. A controller-method
 * {@code @RateLimiter} annotation would run too late — request-body parsing
 * and ContentGuard's decoder pipeline both happen between filter dispatch and
 * method invocation. Implementing as an {@code @Order(HIGHEST_PRECEDENCE + 100)}
 * filter is the only correct placement.
 *
 * <p>On breach, emits RFC 9457 {@code application/problem+json} with a
 * {@code Retry-After} header (per api-contracts.md §1).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class WexRateLimiterFilter extends OncePerRequestFilter {

    private static final String INSTANCE = "apiInbound";

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final String retryAfterSeconds;

    public WexRateLimiterFilter(
            RateLimiterRegistry registry,
            ObjectMapper objectMapper,
            @Value("${wex.ratelimit.permits-per-second:50}") int permits,
            @Value("${wex.ratelimit.timeout-ms:0}") long timeoutMs,
            @Value("${wex.ratelimit.retry-after-seconds:1}") int retryAfter) {
        // If the registry already carries a configured instance from application.yml, reuse it;
        // otherwise install a programmatic config here so the filter is unit-test friendly.
        if (registry.getAllRateLimiters().stream().anyMatch(rl -> INSTANCE.equals(rl.getName()))) {
            this.rateLimiter = registry.rateLimiter(INSTANCE);
        } else {
            RateLimiterConfig cfg = RateLimiterConfig.custom()
                    .limitForPeriod(permits)
                    .limitRefreshPeriod(Duration.ofSeconds(1))
                    .timeoutDuration(Duration.ofMillis(timeoutMs))
                    .build();
            this.rateLimiter = registry.rateLimiter(INSTANCE, cfg);
        }
        this.objectMapper = objectMapper;
        // C2 30-review §4.8 — 429 Retry-After is env-tunable; default 1s matches Resilience4j
        // 1s refresh period. Documented in api-contracts.md §7.
        this.retryAfterSeconds = String.valueOf(retryAfter);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!shouldRateLimit(req)) {
            chain.doFilter(req, res);
            return;
        }
        boolean permitted = rateLimiter.acquirePermission();
        if (!permitted) {
            writeTooManyRequests(req, res);
            return;
        }
        chain.doFilter(req, res);
    }

    /** Visible for tests — exposes the underlying limiter so tests can drain permits deterministically. */
    public RateLimiter underlyingLimiter() {
        return rateLimiter;
    }

    private boolean shouldRateLimit(HttpServletRequest req) {
        String path = req.getRequestURI();
        // Skip actuator + openapi paths — they have their own throttling concerns.
        return !path.startsWith("/actuator") && !path.startsWith("/v3/api-docs") && !path.startsWith("/swagger-ui");
    }

    private void writeTooManyRequests(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setStatus(429);
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setHeader(HttpHeaders.RETRY_AFTER, retryAfterSeconds);
        ObjectNode body = objectMapper.createObjectNode()
                .put("type", "https://wex.example.com/problems/too-many-requests")
                .put("title", "Too Many Requests")
                .put("status", 429)
                .put("errorCode", "RATE_LIMITED")
                .put("instance", req.getRequestURI());
        res.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
