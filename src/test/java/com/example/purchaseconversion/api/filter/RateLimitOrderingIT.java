package com.example.purchaseconversion.api.filter;

import com.example.purchaseconversion.infrastructure.AbstractPostgresIT;
import io.github.resilience4j.ratelimiter.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C 30-review §4.2 — G8-P0-1 servlet-container ordering regression test.
 *
 * <p>The rate-limit filter ({@link WexRateLimiterFilter}) runs at
 * {@code @Order(HIGHEST_PRECEDENCE + 100)}. On rate-limit breach, the filter
 * writes a 429 RFC 9457 envelope and the chain is short-circuited — Bean
 * Validation, {@code ContentGuardAdvice}, and the controller never run. This
 * IT proves the ordering at the full Spring-Boot servlet-container level by:
 *
 * <ol>
 *   <li>Draining the rate-limiter to 0 permits;</li>
 *   <li>Submitting a POST with a Luhn-PAN-bearing description that would
 *       normally trip ContentGuard (400 PAN_PATTERN_DETECTED);</li>
 *   <li>Asserting the response is 429 RATE_LIMITED, NOT 400.</li>
 * </ol>
 *
 * <p>If the filter order ever drifts — e.g., to {@code LOWEST_PRECEDENCE} —
 * the same request would be parsed and bind-checked first, ContentGuard would
 * fire, and the response would be 400 instead of 429. The PAN-bearing payload
 * is the canary that catches that drift.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "wex.ratelimit.permits-per-second=1",
        "wex.ratelimit.timeout-ms=0"
})
class RateLimitOrderingIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private WexRateLimiterFilter rateLimiter;

    @Test
    @DisplayName("AC-T-6 — saturated limiter returns 429 BEFORE ContentGuard runs on a PAN-bearing payload")
    void saturatedLimiterShortsContentGuard() {
        // Step 1: drain the limiter to 0 permits (1 per second; consume the 1).
        RateLimiter limiter = rateLimiter.underlyingLimiter();
        boolean drained = limiter.acquirePermission();
        assertThat(drained).isTrue();

        // Step 2: submit a Luhn-PAN-bearing POST. ContentGuard would normally reject 400 PAN.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"description":"PAN 4242 4242 4242 4242","transactionDate":"2026-05-10","amountUsd":"4.50"}""";

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/purchases", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        // Step 3: status MUST be 429, NOT 400 — proves rate-limit ran BEFORE ContentGuard.
        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getContentType().toString())
                .contains("problem+json");
        assertThat(response.getBody()).contains("RATE_LIMITED");
        // The response body MUST NOT contain "PAN_PATTERN_DETECTED" — that would mean
        // ContentGuard ran, which would be a G8-P0-1 ordering violation.
        assertThat(response.getBody()).doesNotContain("PAN_PATTERN_DETECTED");
        // Belt-and-suspenders: the raw PAN MUST NOT echo in the response (the request
        // never reached deserialisation).
        assertThat(response.getBody()).doesNotContain("4242");
    }
}
