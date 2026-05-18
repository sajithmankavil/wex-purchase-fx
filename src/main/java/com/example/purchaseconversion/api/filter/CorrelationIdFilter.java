package com.example.purchaseconversion.api.filter;

import com.example.purchaseconversion.observability.DescriptionHasher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Server-side {@code X-Correlation-Id} binding per observability.md §2.4 / G4-P1-13.
 *
 * <p>Inbound rule: prepend a service-instance prefix to the client-supplied
 * (or auto-generated) correlation id, producing {@code <svcInstance>-<value>}.
 * The bound value is placed in SLF4J MDC and echoed back as the response
 * header. A separate hashed form is also stored under {@code correlationIdHash}
 * so audit logs can carry a redacted-but-correlatable token.
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} + 200 — AFTER the rate-limit
 * filter (G8-P0-1) so a 429 response is emitted without binding the MDC, but
 * BEFORE any other filter that might log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
public class CorrelationIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Correlation-Id";
    static final String MDC_ID = "correlationId";
    static final String MDC_HASH = "correlationIdHash";
    static final String MDC_REQUEST_ID = "requestId";

    private final String instancePrefix;
    private final DescriptionHasher hasher;

    public CorrelationIdFilter(DescriptionHasher hasher) {
        this.hasher = hasher;
        this.instancePrefix = shortHostPrefix();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String clientValue = req.getHeader(HEADER);
        String bound = bind(clientValue);
        String hashed = hasher.hash(bound);
        String requestId = UUID.randomUUID().toString();
        try {
            MDC.put(MDC_ID, bound);
            MDC.put(MDC_HASH, hashed);
            MDC.put(MDC_REQUEST_ID, requestId);
            res.setHeader(HEADER, bound);
            chain.doFilter(req, res);
        } finally {
            MDC.remove(MDC_ID);
            MDC.remove(MDC_HASH);
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    /** Prepend the instance prefix; generate a UUID if the client didn't supply a value. */
    String bind(String clientValue) {
        String tail = clientValue == null || clientValue.isBlank()
                ? UUID.randomUUID().toString()
                : clientValue.trim();
        return instancePrefix + "-" + tail;
    }

    private static String shortHostPrefix() {
        // Stable per-JVM prefix; not the real hostname (avoids leaking infra detail).
        return "wex-" + Integer.toHexString(System.identityHashCode(CorrelationIdFilter.class) & 0xFFFFFF);
    }
}
