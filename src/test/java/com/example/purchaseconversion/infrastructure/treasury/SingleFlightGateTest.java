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
        // Signals that the winner has acquired the gate AND entered its lambda.
        // We wait deterministically for this rather than Thread.sleep — a slow CI
        // runner can leave the winner unscheduled long enough that a loser becomes
        // a second winner (race observed on the first real CI run that exercised
        // mvn verify; previously the suite never ran on GitHub-hosted runners).
        CountDownLatch winnerInstalled = new CountDownLatch(1);

        ExecutorService exec = Executors.newFixedThreadPool(3);
        try {
            Future<?> winner = exec.submit(() -> gate.runOnce(
                    key,
                    () -> {
                        winnerInstalled.countDown();
                        upstreamCalls.incrementAndGet();
                        try {
                            // Generous timeout: this only fires if the test never
                            // calls winnerHolds.countDown(). The outer Future.get
                            // imposes the real test-failure timeout.
                            winnerHolds.await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        persisted.set(true);
                        return null;
                    },
                    persisted::get));
            // Deterministic wait — winner is now inside its lambda; the gate state
            // is installed; loser submissions are guaranteed to take the loser path
            // (provided losers enter gate.runOnce before the winner's lambda returns).
            assertThat(winnerInstalled.await(5, TimeUnit.SECONDS)).isTrue();

            // Coordination latch — each loser counts down right before calling
            // runOnce. We then wait for both, plus a short scheduling delay to
            // give the losers time to actually enter runOnce + observe the gate.
            CountDownLatch losersAboutToEnter = new CountDownLatch(2);
            Future<?> loser1 = exec.submit(() -> {
                losersAboutToEnter.countDown();
                return gate.runOnce(key,
                        () -> { upstreamCalls.incrementAndGet(); return null; },
                        persisted::get);
            });
            Future<?> loser2 = exec.submit(() -> {
                losersAboutToEnter.countDown();
                return gate.runOnce(key,
                        () -> { upstreamCalls.incrementAndGet(); return null; },
                        persisted::get);
            });
            assertThat(losersAboutToEnter.await(5, TimeUnit.SECONDS)).isTrue();
            // Give losers ~100 ms to enter runOnce + observe the gate before we
            // release the winner. Without this, on a slow CI runner the winner
            // can finish + remove the gate before losers reach runOnce, and the
            // losers each install their own gate and run upstream.
            Thread.sleep(100);

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
        // Deterministic "winner has installed gate state AND entered its lambda" signal —
        // replaces Thread.sleep(50) which was unreliable on slow CI runners (same race
        // pattern as twoLosersOneWinner; surfaced by the first real CI run).
        CountDownLatch winnerInstalled = new CountDownLatch(1);

        ExecutorService exec = Executors.newFixedThreadPool(2);
        try {
            Future<?> winner = exec.submit(() -> {
                try {
                    return gate.runOnce(key, () -> {
                        winnerInstalled.countDown();
                        // Generous timeout — see twoLosersOneWinner for rationale.
                        try { winnerHolds.await(30, TimeUnit.SECONDS); }
                        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        throw new UpstreamUnavailableException("timeout");
                    }, () -> false);
                } catch (UpstreamUnavailableException e) {
                    return null;  // swallow — we're asserting the loser's behaviour
                }
            });
            assertThat(winnerInstalled.await(5, TimeUnit.SECONDS)).isTrue();

            CountDownLatch loserAboutToEnter = new CountDownLatch(1);
            Future<UpstreamUnavailableException> loser = exec.submit(() -> {
                loserAboutToEnter.countDown();
                try {
                    gate.runOnce(key, () -> { throw new AssertionError("loser ran upstream"); },
                            () -> false);
                    return null;
                } catch (UpstreamUnavailableException e) {
                    return e;
                }
            });
            assertThat(loserAboutToEnter.await(5, TimeUnit.SECONDS)).isTrue();
            // Give the loser ~100 ms to enter runOnce + observe the gate before
            // we release the winner (see twoLosersOneWinner for rationale).
            Thread.sleep(100);

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
