package com.example.purchaseconversion.api.advice;

import com.example.purchaseconversion.api.advice.exception.PanPatternDetectedException;
import com.example.purchaseconversion.application.exception.InvalidCurrencyException;
import com.example.purchaseconversion.application.exception.MalformedIdentifierException;
import com.example.purchaseconversion.observability.DescriptionHasher;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C 30-review §4.1 + AC-032/032b — application-log emissions must NEVER carry
 * raw {@code description}, raw {@code currency}, or raw {@code id} text. The
 * advice handlers ({@link ProblemDetailExceptionHandler}) emit only the HMAC
 * hashed form via {@link DescriptionHasher}; this test pins that behaviour by
 * capturing every log event the handler produces and asserting the rejected
 * payload's substring is absent at every log level.
 *
 * <p>Runs in BOTH {@code local} (default DescriptionHasher with v0 prefix) and
 * a simulated {@code ci} profile (env-keyed DescriptionHasher with v1+ prefix)
 * per G8-P1-10.
 */
class LoggingPiiGuardTest {

    /** A Luhn-valid PAN — the attacker-controlled fixture we never want to see in logs. */
    private static final String PAN = "4242424242424242";
    /** A fullwidth-confusable variant for AC-010e parallel coverage. */
    private static final String FULLWIDTH_PAN = "４２４２ ４２４２ ４２４２ ４２４２";

    private ListAppender<ILoggingEvent> appender;
    private Logger rootLogger;

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

    @Nested
    @DisplayName("local profile (DescriptionHasher v0 fallback)")
    class LocalProfile {

        private final DescriptionHasher hasher = new DescriptionHasher("local", "", "v1");
        private final ProblemDetailExceptionHandler handler = new ProblemDetailExceptionHandler(hasher);

        @Test
        @DisplayName("AC-010b — ContentGuard rejection log carries the reason label only; no raw description")
        void contentGuardLogsNoRawDescription() {
            handler.onPan(new PanPatternDetectedException("luhn"));
            assertNoRawIn(allMessages(), PAN);
            assertNoRawIn(allMessages(), FULLWIDTH_PAN);
            assertContains(allMessages(), "purchase_validation_failed");
            assertContains(allMessages(), "reason=luhn");
        }

        @Test
        @DisplayName("A2 §5 — InvalidCurrencyException advice log carries currency.hash (vN:) + length only")
        void invalidCurrencyLogsHashedOnly() {
            handler.onInvalidCurrency(new InvalidCurrencyException(PAN));
            assertNoRawIn(allMessages(), PAN);
            assertContains(allMessages(), "currency_alias.drift.detected");
            assertContains(allMessages(), "v0:");                   // hashed prefix
            assertContains(allMessages(), "currencyLength=" + PAN.length());
        }

        @Test
        @DisplayName("C 30-review §4.7 — MalformedIdentifierException advice log carries id.hash + length only")
        void malformedIdentifierLogsHashedOnly() {
            handler.onMalformedId(new MalformedIdentifierException(PAN));
            assertNoRawIn(allMessages(), PAN);
            assertContains(allMessages(), "malformed_identifier.detected");
            assertContains(allMessages(), "v0:");
            assertContains(allMessages(), "idLength=" + PAN.length());
        }

        @Test
        @DisplayName("AC-010e — fullwidth confusable inputs are also redacted")
        void fullwidthCurrencyHashedOnly() {
            handler.onInvalidCurrency(new InvalidCurrencyException(FULLWIDTH_PAN));
            assertNoRawIn(allMessages(), FULLWIDTH_PAN);
            // ASCII '4242' substring also absent (the hash never contains source digits).
            assertNoRawIn(allMessages(), "4242");
        }
    }

    @Nested
    @DisplayName("ci profile (DescriptionHasher with env key + v1+ prefix)")
    class CiProfile {

        private final DescriptionHasher hasher = new DescriptionHasher("ci", "ci-rotation-key-2026q2", "v1");
        private final ProblemDetailExceptionHandler handler = new ProblemDetailExceptionHandler(hasher);

        @Test
        @DisplayName("InvalidCurrencyException — vN: prefix is v1 in ci profile; no raw value")
        void invalidCurrencyCiPrefix() {
            handler.onInvalidCurrency(new InvalidCurrencyException(PAN));
            assertNoRawIn(allMessages(), PAN);
            assertContains(allMessages(), "v1:");
        }

        @Test
        @DisplayName("MalformedIdentifierException — vN: prefix is v1 in ci profile; no raw value")
        void malformedIdCiPrefix() {
            handler.onMalformedId(new MalformedIdentifierException(PAN));
            assertNoRawIn(allMessages(), PAN);
            assertContains(allMessages(), "v1:");
        }
    }

    @Nested
    @DisplayName("Cross-level scan — no raw value at TRACE / DEBUG / INFO / WARN / ERROR")
    class CrossLevel {

        private final DescriptionHasher hasher = new DescriptionHasher("local", "", "v1");
        private final ProblemDetailExceptionHandler handler = new ProblemDetailExceptionHandler(hasher);

        @ParameterizedTest
        @ValueSource(strings = {"4242424242424242", "5555555555554444", "378282246310005"})
        @DisplayName("multiple Luhn-PAN fixtures — none appear at any log level")
        void noPanAtAnyLevel(String pan) {
            handler.onMalformedId(new MalformedIdentifierException(pan));
            handler.onInvalidCurrency(new InvalidCurrencyException(pan));
            handler.onPan(new PanPatternDetectedException("luhn"));

            for (Level level : List.of(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR)) {
                String levelText = messagesAtLevel(level);
                assertThat(levelText)
                        .as("level %s must not contain raw PAN substring", level)
                        .doesNotContain(pan);
            }
        }
    }

    private List<String> allMessages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
    }

    private String messagesAtLevel(Level level) {
        return appender.list.stream()
                .filter(e -> e.getLevel().equals(level))
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    private static void assertNoRawIn(List<String> messages, String forbidden) {
        for (String msg : messages) {
            assertThat(msg)
                    .as("log message must not contain raw value")
                    .doesNotContain(forbidden);
        }
    }

    private static void assertContains(List<String> messages, String expected) {
        assertThat(messages.stream().anyMatch(m -> m.contains(expected)))
                .as("messages should contain %s; got %s", expected, messages)
                .isTrue();
    }
}
