package com.example.purchaseconversion.observability;

import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Call-level audit logging for the benefit-eligibility check
 * (eligibility-endpoint-spec.md §3.4).
 *
 * <p>Emits exactly one structured log line per call with {@code benefitId},
 * {@code tier}, {@code eligible}, and {@code latencyMs}. Correlation id is NOT
 * passed explicitly — {@code CorrelationIdFilter} already binds it into SLF4J MDC,
 * and the logstash encoder lifts MDC keys into the JSON output automatically.
 *
 * <p><b>Deliberately excludes any cardholder/session identifier.</b> The endpoint's
 * inputs (spec §2) carry no such field, so this is call-level audit only, not
 * cardholder-level (spec §5 scope-affecting #1). The absence of a PII field here is
 * a regression-tested requirement, not just a design note — see
 * {@code EligibilityAuditLoggerTest}.
 */
@Component
public class EligibilityAuditLogger {

    private static final Logger LOG = LoggerFactory.getLogger(EligibilityAuditLogger.class);

    public void logCheck(String benefitId, String tier, boolean eligible, long latencyMs) {
        LOG.info("benefit_eligibility.checked",
                StructuredArguments.kv("benefitId", benefitId),
                StructuredArguments.kv("tier", tier),
                StructuredArguments.kv("eligible", eligible),
                StructuredArguments.kv("latencyMs", latencyMs));
    }
}
