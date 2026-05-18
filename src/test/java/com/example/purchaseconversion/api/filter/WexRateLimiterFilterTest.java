package com.example.purchaseconversion.api.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link WexRateLimiterFilter} — covers the G8-P0-1 ordering
 * intent: when the limiter rejects a request, the filter writes a 429 RFC 9457
 * envelope and the downstream chain (ContentGuard, controllers) is NEVER invoked.
 */
class WexRateLimiterFilterTest {

    private WexRateLimiterFilter filter;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        // 1 permit per second; timeout 0 so the second call within a second is rejected.
        filter = new WexRateLimiterFilter(RateLimiterRegistry.ofDefaults(), mapper, 1, 0);
    }

    @Test
    @DisplayName("first call passes through; downstream chain invoked")
    void firstCallPasses() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/purchases");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("G8-P0-1 — second call within the same second is REJECTED before the chain runs")
    void secondCallRejected() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // First call drains the 1-permit budget.
        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/purchases"),
                new MockHttpServletResponse(), chain);

        // Second call (same second) must NOT invoke the chain.
        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/purchases");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(req2, res2, chain);

        // Chain invoked exactly once (the first call), NOT for the rejected second call.
        verify(chain, times(1)).doFilter(any(), any());
        assertThat(res2.getStatus()).isEqualTo(429);
        assertThat(res2.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(res2.getHeader("Retry-After")).isEqualTo("1");
        assertThat(res2.getContentAsString()).contains("\"errorCode\":\"RATE_LIMITED\"");
    }

    @Test
    @DisplayName("actuator paths bypass the limiter")
    void actuatorBypasses() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        // Drain the budget.
        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/purchases"),
                new MockHttpServletResponse(), chain);
        // Actuator call should still pass even though the budget is exhausted.
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);

        verify(chain, times(2)).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(200);
    }
}
