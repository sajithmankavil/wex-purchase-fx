package com.example.purchaseconversion.infrastructure.treasury;

import com.example.purchaseconversion.application.exception.UpstreamUnavailableException;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-tests for {@link SingleFlightGate}. The gate is tested standalone (no
 * Spring context) — its semantics don't depend on the surrounding adapter.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@link SingleFlightGate.Key#ceilToQuarterEnd} — Q1/Q2/Q3/Q4 ceilings; AC-027e key.</li>
 *   <li>Winner runs the supplier exactly once.</li>
 *   <li>Loser short-circuits via the DB-poll callback (success path).</li>
 *   <li>Loser mirrors the winner's exception (AC-027d).</li>
 *   <li>Loser times out → {@code loser_timeout}.</li>
 *   <li>{@link SingleFlightGate#releaseAll} unblocks losers with {@code shutdown}.</li>
 *   <li>Two losers + one winner all converge on a single upstream call (AC-027b).</li>
 * </ul>
 */
class SingleFlightGateTest {

    private static final CurrencyDescriptor CAD = CurrencyDescriptor.of("Canada-Dollar");

    @Test
    @DisplayName("ceilToQuarterEnd — Q1/Q2/Q3/Q4 boundaries")
    void quarterEndCeiling() {
        assertThat(SingleFlightGate.Key.ceilToQuarterEnd(LocalDate.of(2026, 1, 1)))
                .isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(SingleFlightGate.Key.ceilToQuarterEnd(LocalDate.of(2026, 4, 1)))
                .isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(SingleFlightGate.Key.ceilToQuarterEnd(LocalDate.of(2026, 9, 30)))
                .isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(SingleFlightGate.Key.ceilToQuarterEnd(LocalDate.of(2026, 10, 1)))
                .isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(SingleFlightGate.Key.ceilToQuarterEnd(LocalDate.of(2026, 12, 31)))
                .isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("AC-027e — purchases in the same quarter share a gate key")
    void sameQuarterSharesKey() {
        SingleFlightGate.Key apr1 = SingleFlightGate.Key.forTransactionDate(CAD, LocalDate.of(2026, 4, 1));
        SingleFlightGate.Key jun30 = SingleFlightGate.Key.forTransactionDate(CAD, LocalDate.of(2026, 6, 30));
        SingleFlightGate.Key jul1 = SingleFlightGate.Key.forTransactionDate(CAD, LocalDate.of(2026, 7, 1));
        assertThat(apr1).isEqualTo(jun30);
        assertThat(apr1).isNotEqualTo(jul1);
    }

    @Test
    @DisplayName("winner runs supplier exactly once")
    void winnerRunsOnce() {
        SingleFlightGate gate = new SingleFlightGate(10_000L, 100L);
        AtomicInteger calls = new AtomicInteger();
        SingleFlightGate.Key key = SingleFlightGate.Key.forTransactionDate(CAD, LocalDate.of(2026, 5, 1));

        SingleFlightGate.Outcome outcome = gate.runOnce(
                key, () -> { calls.incrementAndGet(); return null; }, () -> false);

        assertThat(outcome).isEqualTo(SingleFlightGate.Outcome.WINNER_SUCCESS);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(gate.activeGateCount()).isZero();
    }

    @Test
    @DisplayName("AC-027b — two losers + one winner cause exactly one upstream call")
    void twoLosersOneWinner() throws Exception {
        SingleFlightGate gate = new SingleFlightGate(10_000L, 50L);
        SingleFlightGate.Key key = SingleFlightGate.Key.forTransactionDate(CAD, LocalDate.of(2026, 5, 1));
        AtomicInteger upstreamCalls = new AtomicInteger();
        AtomicBoolean persisted = new AtomicBoolean(false);
        CountDownLatch winnerHolds = new CountDownLatch(1);

        ExecutorService exec = Executors.newFixedThreadPool(3);
        try {
            Future<?> winner = exec.submit(() -> gate.runOnce(
                    key,
                    () -> {
                        upstreamCalls.incrementAndGet();
                        try {
                            winnerHolds.await(2, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        persisted.set(true);
                        return null;
                    },
                    persisted::get));
            // Give the winner a chance to install its state.
            Thread.sleep(50);
            Future<?> loser1 = exec.submit(() -> gate.runOnce(key,
                    () -> { upstreamCalls.incrementAndGet(); return null; },
                    persisted::get));
            Future<?> loser2 = exec.submit(() -> gate.runOnce(key,
                    () -> { upstreamCalls.incrementAndGet(); return null; },
                    persisted::get));

            // Release the winner.
            winnerHolds.countDown();
            winner.get(5, TimeUnit.SECONDS);
            loser1.get(5, TimeUnit.SECONDS);
            loser2.get(5, TimeUnit.SECONDS);

            assertThat(upstreamCalls.get()).isEqualTo(1);
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    @DisplayName("AC-027d — loser mirrors the winner's exception")
    void loserMirrorsWinnerFailure() throws Exception {
        SingleFlightGate gate = new SingleFlightGate(10_000L, 50L);
        SingleFlightGate.Key key = SingleFlightGate.Key.forTransactionDate(CAD, LocalDate.of(2026, 5, 1));
        CountDownLatch winnerHolds = new CountDownLatch(1);

        ExecutorService exec = Executors.newFixedThreadPool(2);
        try {
            Future<?> winner = exec.submit(() -> {
                try {
                    return gate.runOnce(key, () -> {
                        try { winnerHolds.await(2, TimeUnit.SECONDS); }
                        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        throw new UpstreamUnavailableException("timeout");
                    }, () -> false);
                } catch (UpstreamUnavailableException e) {
                    return null;  // swallow — we're asserting the loser's behaviour
                }
            });
            Thread.sleep(50);

            Future<UpstreamUnavailableException> loser = exec.submit(() -> {
                try {
                    gate.runOnce(key, () -> { throw new AssertionError("loser ran upstream"); },
                            () -> false);
                    return null;
                } catch (UpstreamUnavailableException e) {
                    return e;
                }
            });

            winnerHolds.countDown();
            winner.get(5, TimeUnit.SECONDS);
            UpstreamUnavailableException loserException = loser.get(5, TimeUnit.SECONDS);
            assertThat(loserException).isNotNull();
            // Mirrors winner's reason
            assertThat(loserException.getReason()).isEqualTo("timeout");
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    @DisplayName("loser times out with reason=loser_timeout when DB never sees the result and winner stays running")
    void loserTimesOut() throws Exception {
        SingleFlightGate gate = new SingleFlightGate(300L, 50L);
        SingleFlightGate.Key key = SingleFlightGate.Key.forTransactionDate(CAD, LocalDate.of(2026, 5, 1));
        CountDownLatch hold = new CountDownLatch(1);

        ExecutorService exec = Executors.newFixedThreadPool(2);
        try {
            Future<?> winner = exec.submit(() -> gate.runOnce(key, () -> {
                try { hold.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                return null;
            }, () -> false));
            Thread.sleep(30);

            Future<UpstreamUnavailableException> loser = exec.submit(() -> {
                try {
                    gate.runOnce(key, () -> null, () -> false);
                    return null;
                } catch (UpstreamUnavailableException e) {
                    return e;
                }
            });
            UpstreamUnavailableException ex = loser.get(2, TimeUnit.SECONDS);
            assertThat(ex).isNotNull();
            assertThat(ex.getReason()).isEqualTo("loser_timeout");

            hold.countDown();
            winner.get(2, TimeUnit.SECONDS);
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    @DisplayName("G4-P1-23 — releaseAll causes new callers to fail-fast with shutdown")
    void releaseAllShortCircuits() {
        SingleFlightGate gate = new SingleFlightGate(10_000L, 50L);
        gate.releaseAll();

        assertThatThrownBy(() -> gate.runOnce(
                SingleFlightGate.Key.forTransactionDate(CAD, LocalDate.of(2026, 5, 1)),
                () -> null, () -> false))
                .isInstanceOf(UpstreamUnavailableException.class)
                .satisfies(t -> assertThat(((UpstreamUnavailableException) t).getReason()).isEqualTo("shutdown"));
    }

    @Test
    @DisplayName("releaseAll while a loser is waiting → loser exits with shutdown reason")
    void releaseAllUnblocksLoser() throws Exception {
        SingleFlightGate gate = new SingleFlightGate(5_000L, 50L);
        SingleFlightGate.Key key = SingleFlightGate.Key.forTransactionDate(CAD, LocalDate.of(2026, 5, 1));
        CountDownLatch winnerHolds = new CountDownLatch(1);

        ExecutorService exec = Executors.newFixedThreadPool(2);
        try {
            Future<?> winner = exec.submit(() -> {
                try {
                    return gate.runOnce(key, () -> {
                        try { winnerHolds.await(5, TimeUnit.SECONDS); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        return null;
                    }, () -> false);
                } catch (UpstreamUnavailableException e) {
                    return null;  // winner may also see shutdown if outcome was flipped
                }
            });
            Thread.sleep(50);

            Future<UpstreamUnavailableException> loser = exec.submit(() -> {
                try {
                    gate.runOnce(key, () -> null, () -> false);
                    return null;
                } catch (UpstreamUnavailableException e) {
                    return e;
                }
            });
            Thread.sleep(100);
            gate.releaseAll();
            UpstreamUnavailableException ex = loser.get(2, TimeUnit.SECONDS);
            assertThat(ex).isNotNull();
            assertThat(ex.getReason()).isEqualTo("shutdown");

            winnerHolds.countDown();
            winner.get(2, TimeUnit.SECONDS);
        } finally {
            exec.shutdownNow();
        }
    }
}
