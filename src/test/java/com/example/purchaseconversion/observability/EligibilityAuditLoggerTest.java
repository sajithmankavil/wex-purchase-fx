package com.example.purchaseconversion.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * eligibility-endpoint-spec.md §3.4 / §5 scope-affecting #1 — the audit log line must
 * carry benefitId/tier/eligible/latencyMs and MUST NOT carry any cardholder/session/PII
 * field, since the endpoint's inputs don't include one. This pins that absence as a
 * regression-tested requirement, mirroring {@code LoggingPiiGuardTest}'s approach for
 * the purchase-conversion advice handlers.
 */
class EligibilityAuditLoggerTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger rootLogger;
    private final EligibilityAuditLogger auditLogger = new EligibilityAuditLogger();

    @BeforeEach
    void attachAppender() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        rootLogger.detachAppender(appender);
    }

    @Test
    @DisplayName("logs exactly one line carrying benefitId, tier, eligible, latencyMs")
    void logsExpectedFields() {
        auditLogger.logCheck("BEN-1042", "SIGNATURE", true, 7L);

        List<String> messages = allMessages();
        assertThat(messages).hasSize(1);
        String line = messages.get(0);
        assertThat(line).contains("benefit_eligibility.checked");
        assertThat(line).contains("benefitId=BEN-1042");
        assertThat(line).contains("tier=SIGNATURE");
        assertThat(line).contains("eligible=true");
        assertThat(line).contains("latencyMs=7");
    }

    @Test
    @DisplayName("never carries a cardholder/session/PII field — the contract has no such input (spec §3.4)")
    void neverCarriesCardholderIdentifyingField() {
        auditLogger.logCheck("BEN-1042", "SIGNATURE", true, 7L);

        String line = allMessages().get(0);
        assertThat(line).doesNotContainIgnoringCase("cardholder");
        assertThat(line).doesNotContainIgnoringCase("session");
        assertThat(line).doesNotContainIgnoringCase("accountId");
        assertThat(line).doesNotContainIgnoringCase("customerId");
    }

    private List<String> allMessages() {
        return appender.list.stream()
                .map(EligibilityAuditLoggerTest::renderEvent)
                .collect(Collectors.toList());
    }

    private static String renderEvent(ILoggingEvent event) {
        StringBuilder sb = new StringBuilder(event.getFormattedMessage());
        Object[] args = event.getArgumentArray();
        if (args != null) {
            for (Object arg : args) {
                if (arg != null) {
                    sb.append(' ').append(arg);
                }
            }
        }
        return sb.toString();
    }
}
