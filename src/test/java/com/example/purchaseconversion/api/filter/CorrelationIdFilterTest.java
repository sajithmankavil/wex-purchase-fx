package com.example.purchaseconversion.api.filter;

import com.example.purchaseconversion.observability.DescriptionHasher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the server-side {@code X-Correlation-Id} binding
 * (observability.md §2.4 / G4-P1-13).
 */
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter(new DescriptionHasher("test", "", "v1"));
        MDC.clear();
    }

    @Test
    @DisplayName("Inbound client value is prefixed with the instance label")
    void prefixesClientValue() {
        String bound = filter.bind("abc-123");
        assertThat(bound).startsWith("wex-").endsWith("-abc-123");
    }

    @Test
    @DisplayName("Missing client value falls back to a generated UUID")
    void generatesWhenAbsent() {
        String bound = filter.bind(null);
        assertThat(bound).matches("wex-[0-9a-f]+-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("Filter binds MDC + echoes header + clears MDC after chain")
    void mdcLifecycle() throws Exception {
        HttpServletRequest req = new MockHttpServletRequest();
        ((MockHttpServletRequest) req).addHeader("X-Correlation-Id", "ext-42");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (r, s) -> seen.set(MDC.get(CorrelationIdFilter.MDC_ID));

        filter.doFilter(req, res, chain);

        assertThat(seen.get()).startsWith("wex-").endsWith("-ext-42");
        assertThat(res.getHeader("X-Correlation-Id")).isEqualTo(seen.get());
        // MDC was cleared in the finally block.
        assertThat(MDC.get(CorrelationIdFilter.MDC_ID)).isNull();
        assertThat(MDC.get(CorrelationIdFilter.MDC_HASH)).isNull();
        assertThat(MDC.get(CorrelationIdFilter.MDC_REQUEST_ID)).isNull();
    }
}
