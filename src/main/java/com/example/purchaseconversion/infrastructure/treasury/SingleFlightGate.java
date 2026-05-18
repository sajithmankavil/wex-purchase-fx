package com.example.purchaseconversion.infrastructure.treasury;

import com.example.purchaseconversion.application.exception.UpstreamUnavailableException;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.observability.MetricsCatalog;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Per-{@code (currency, treasury_quarter_end)} single-flight gate (ADR-0001 D-9;
 * AC-027b/c/d/e; G4-P0-1 + G6-P0-2).
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li>The winner is the first thread to insert a {@link State} for a given
 *       {@link Key}; it runs the supplier (typically the Treasury fetch + upsert).</li>
 *   <li>Losers — concurrent callers that find a State already in place — do NOT
 *       block on the winner's future. They poll the database via the supplied
 *       {@link BooleanSupplier} every {@code dbPollIntervalMillis} for up to
 *       {@code loserWaitMillis}.</li>
 *   <li>If the loser's DB poll returns {@code true} (the winner's result has
 *       persisted), the loser returns successfully without an upstream call.</li>
 *   <li>If the winner's {@link State#outcome} flips to a failure before either of
 *       those, the loser mirrors the winner's exact exception type via
 *       {@link UpstreamUnavailableException} so AC-027d's "consistent outcomes"
 *       contract holds.</li>
 *   <li>If neither signal fires within {@code loserWaitMillis}, the loser throws
 *       {@code UpstreamUnavailableException("loser_timeout")}.</li>
 *   <li>On SIGTERM (Spring {@code @PreDestroy}), {@link #releaseAll()} flips every
 *       state to FAIL; in-flight losers exit immediately with
 *       {@code UpstreamUnavailableException("shutdown")}.</li>
 * </ul>
 *
 * <p>Gate keying — {@code (currency, treasury_quarter_end)} where
 * {@code treasury_quarter_end = ceil(transactionDate, quarter-end)} — matches the
 * granularity Treasury publishes at (AC-027e). All purchases whose
 * {@code transactionDate} falls in the same quarter dedupe to one upstream call.
 */
@Component
public class SingleFlightGate {

    private static final Logger LOG = LoggerFactory.getLogger(SingleFlightGate.class);

    private final long loserWaitMillis;
    private final long dbPollIntervalMillis;

    private final ConcurrentHashMap<Key, State> gates = new ConcurrentHashMap<>();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    /**
     * C3 §S4 carry-forward — wired by Spring via setter (autowired-required-false) so
     * existing unit tests can construct the gate without a catalog.
     */
    private MetricsCatalog metrics;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setMetrics(MetricsCatalog metrics) {
        this.metrics = metrics;
    }

    public SingleFlightGate(
            @Value("${wex.single-flight.loser-wait-ms:10000}") long loserWaitMillis,
            @Value("${wex.single-flight.db-poll-interval-ms:100}") long dbPollIntervalMillis) {
        if (loserWaitMillis <= 0) {
            throw new IllegalArgumentException("loserWaitMillis must be > 0; got " + loserWaitMillis);
        }
        if (dbPollIntervalMillis <= 0 || dbPollIntervalMillis > loserWaitMillis) {
            throw new IllegalArgumentException(
                    "dbPollIntervalMillis must be > 0 and <= loserWaitMillis; got "
                            + dbPollIntervalMillis + " / " + loserWaitMillis);
        }
        this.loserWaitMillis = loserWaitMillis;
        this.dbPollIntervalMillis = dbPollIntervalMillis;
    }

    /**
     * Runs {@code winnerAction} under the gate identified by {@code key}. If a
     * gate is already held, the calling thread becomes a loser and polls
     * {@code dbHasResult} until either: (a) the DB returns true (winner persisted
     * its result), (b) the winner's outcome ref shows failure (loser throws), or
     * (c) the wait expires.
     */
    public <T> Outcome runOnce(
            Key key, Supplier<T> winnerAction, BooleanSupplier dbHasResult) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(winnerAction, "winnerAction must not be null");
        Objects.requireNonNull(dbHasResult, "dbHasResult must not be null");

        if (shuttingDown.get()) {
            throw new UpstreamUnavailableException("shutdown");
        }

        State state = new State();
        State existing = gates.putIfAbsent(key, state);

        if (existing == null) {
            // Winner path.
            return runAsWinner(key, state, winnerAction);
        } else {
            // Loser path — coordinate via the existing state.
            return waitAsLoser(key, existing, dbHasResult);
        }
    }

    /** Visible for testing — current gate population. */
    int activeGateCount() {
        return gates.size();
    }

    /**
     * Spring-managed shutdown hook (G4-P1-23). Flips every in-flight winner's
     * outcome to FAIL("shutdown") so any waiting losers exit immediately.
     */
    @PreDestroy
    public void releaseAll() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }
        LOG.info("single_flight.shutdown.start gates={}", gates.size());
        UpstreamUnavailableException reason = new UpstreamUnavailableException("shutdown");
        gates.forEach((key, state) -> state.outcome.compareAndSet(null, WinnerOutcome.failure(reason)));
        gates.clear();
    }

    private <T> Outcome runAsWinner(Key key, State state, Supplier<T> winnerAction) {
        try {
            winnerAction.get();
            state.outcome.set(WinnerOutcome.success());
            return Outcome.WINNER_SUCCESS;
        } catch (RuntimeException e) {
            state.outcome.set(WinnerOutcome.failure(e));
            throw e;
        } finally {
            gates.remove(key, state);
        }
    }

    private Outcome waitAsLoser(Key key, State state, BooleanSupplier dbHasResult) {
        long deadline = System.currentTimeMillis() + loserWaitMillis;
        while (System.currentTimeMillis() < deadline) {
            if (shuttingDown.get()) {
                emitLoserOutcome("shutdown");
                throw new UpstreamUnavailableException("shutdown");
            }
            WinnerOutcome outcome = state.outcome.get();
            if (outcome != null && !outcome.success()) {
                // Winner failed; mirror its exception type. AC-027d.
                emitLoserOutcome("mirrored_failure");
                throw mirror(outcome.failure());
            }
            if (dbHasResult.getAsBoolean()) {
                emitLoserOutcome("db_hit");
                return Outcome.LOSER_DB_HIT;
            }
            // Outcome might be SUCCESS but DB poll hasn't seen it yet — short sleep,
            // then re-check via the loop. Also covers the timing-window between the
            // winner's outcome flip and DB commit (single connection in the test,
            // multi-replica in prod).
            try {
                Thread.sleep(dbPollIntervalMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                emitLoserOutcome("interrupted");
                throw new UpstreamUnavailableException("loser_interrupted", ie);
            }
        }
        LOG.warn("single_flight.loser.timeout key={} waitMs={}", key, loserWaitMillis);
        emitLoserOutcome("timeout");
        throw new UpstreamUnavailableException("loser_timeout");
    }

    /** C3 §S4 carry-forward — emit single_flight.loser_outcome{type=<...>} on every loser exit. */
    private void emitLoserOutcome(String type) {
        if (metrics != null) {
            metrics.singleFlightLoserOutcome(type);
        }
    }

    private static UpstreamUnavailableException mirror(RuntimeException winnerFailure) {
        // AC-027d: loser receives the same exception type as the winner. If the
        // winner failed with an UpstreamUnavailableException, propagate the same
        // reason; otherwise wrap.
        if (winnerFailure instanceof UpstreamUnavailableException uue) {
            return new UpstreamUnavailableException(uue.getReason(), uue);
        }
        return new UpstreamUnavailableException(
                "winner_failed:" + winnerFailure.getClass().getSimpleName(), winnerFailure);
    }

    /** Identity for the gate. Treasury's publish granularity is quarter-end (AC-027e). */
    public record Key(CurrencyDescriptor currency, LocalDate treasuryQuarterEnd) {
        public Key {
            Objects.requireNonNull(currency, "currency must not be null");
            Objects.requireNonNull(treasuryQuarterEnd, "treasuryQuarterEnd must not be null");
        }

        /** Convenience: compute the gate key from a transaction date. */
        public static Key forTransactionDate(CurrencyDescriptor currency, LocalDate transactionDate) {
            return new Key(currency, ceilToQuarterEnd(transactionDate));
        }

        /** quarter-end ceiling per Treasury's quarterly publish (AC-027e). */
        public static LocalDate ceilToQuarterEnd(LocalDate date) {
            int month = date.getMonthValue();
            int quarter = (month - 1) / 3;
            int endMonth = quarter * 3 + 3;
            return LocalDate.of(date.getYear(), endMonth, 1).withDayOfMonth(
                    LocalDate.of(date.getYear(), endMonth, 1).lengthOfMonth());
        }
    }

    /** Per-key gate state; outcome is null while the winner is running. */
    private static final class State {
        final AtomicReference<WinnerOutcome> outcome = new AtomicReference<>();
    }

    /** Final outcome shape recorded by the winner. */
    private record WinnerOutcome(boolean success, RuntimeException failure) {
        static WinnerOutcome success() {
            return new WinnerOutcome(true, null);
        }

        static WinnerOutcome failure(RuntimeException e) {
            return new WinnerOutcome(false, Objects.requireNonNull(e, "failure must not be null"));
        }
    }

    /** Caller-facing return value — tells the caller whether they ran the upstream call. */
    public enum Outcome {
        WINNER_SUCCESS,
        LOSER_DB_HIT
    }
}
