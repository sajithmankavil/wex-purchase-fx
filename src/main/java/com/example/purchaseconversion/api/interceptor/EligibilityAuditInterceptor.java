package com.example.purchaseconversion.api.interceptor;

import com.example.purchaseconversion.observability.EligibilityAuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;

/**
 * Fires the benefit-eligibility audit line <b>after the response has been sent</b>,
 * best-effort (eligibility-endpoint-spec.md §3.4).
 *
 * <h2>Why after-response, not inline in the controller</h2>
 *
 * <p>Audit logging is observability, not business logic — it must never add latency
 * to, or risk failing, the client-visible response. {@link #afterCompletion} runs
 * once the response has already been committed back to the client, so:
 * <ul>
 *   <li>a slow or misbehaving logger cannot delay the response the caller sees;</li>
 *   <li>any exception here is caught and swallowed (logged at WARN, never rethrown)
 *       — by this point there is no response left to fail.</li>
 * </ul>
 *
 * <p>The controller ({@code BenefitEligibilityController}) sets the
 * {@link #ATTR_BENEFIT_ID}, {@link #ATTR_TIER}, and {@link #ATTR_ELIGIBLE} request
 * attributes only when it reaches an actual eligibility determination (the
 * {@code Eligible} / {@code NotEligible} outcomes); {@link #ATTR_MINIMUM_TIER} is
 * additionally set for {@code NotEligible} only. {@link #preHandle} stamps the start time so latency
 * is measured from "request received" through to "response sent" — the same window
 * the p99 &lt; 100ms NFR describes (spec §4.1) — not just the in-handler compute time.
 *
 * <p><b>Deliberately does not log for error paths</b> (400 invalid/missing tier, 404
 * benefit not found) — those aren't eligibility determinations, and are already
 * observable via the RFC 9457 error response / HTTP status. Logging "no attributes
 * present" as if it were a check would misrepresent what happened.
 */
@Component
public class EligibilityAuditInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(EligibilityAuditInterceptor.class);

    public static final String ATTR_START_NANOS = "eligibility.audit.startNanos";
    public static final String ATTR_BENEFIT_ID = "eligibility.audit.benefitId";
    public static final String ATTR_TIER = "eligibility.audit.tier";
    public static final String ATTR_ELIGIBLE = "eligibility.audit.eligible";
    /** Set only for a NotEligible outcome — absent (not stamped) for Eligible. */
    public static final String ATTR_MINIMUM_TIER = "eligibility.audit.minimumTier";

    private final EligibilityAuditLogger auditLogger;

    public EligibilityAuditInterceptor(EligibilityAuditLogger auditLogger) {
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger must not be null");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTR_START_NANOS, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            emit(request);
        } catch (RuntimeException e) {
            // Best-effort per spec §3.4: the response has already been sent, so this
            // can only ever be a logging problem, never a caller-visible one.
            LOG.warn("eligibility.audit.failed errClass={}", e.getClass().getSimpleName());
        }
    }

    private void emit(HttpServletRequest request) {
        Object startNanos = request.getAttribute(ATTR_START_NANOS);
        Object benefitId = request.getAttribute(ATTR_BENEFIT_ID);
        Object tier = request.getAttribute(ATTR_TIER);
        Object eligible = request.getAttribute(ATTR_ELIGIBLE);
        if (startNanos == null || benefitId == null || tier == null || eligible == null) {
            return; // no eligibility determination was reached on this request (see class javadoc)
        }
        String minimumTier = (String) request.getAttribute(ATTR_MINIMUM_TIER); // null for Eligible, by design
        long latencyMs = (System.nanoTime() - (long) startNanos) / 1_000_000;
        auditLogger.logCheck((String) benefitId, (String) tier, (boolean) eligible, minimumTier, latencyMs);
    }
}
