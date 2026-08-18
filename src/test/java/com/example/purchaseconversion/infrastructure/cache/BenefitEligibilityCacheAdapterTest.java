package com.example.purchaseconversion.infrastructure.cache;

import com.example.purchaseconversion.domain.BenefitEligibility;
import com.example.purchaseconversion.domain.BenefitId;
import com.example.purchaseconversion.domain.CardTier;
import com.example.purchaseconversion.infrastructure.persistence.BenefitEligibilityRepoAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenefitEligibilityCacheAdapterTest {

    private static final BenefitId BENEFIT_A = BenefitId.of("BEN-A");
    private static final BenefitId BENEFIT_B = BenefitId.of("BEN-B");

    @Mock
    private BenefitEligibilityRepoAdapter repository;

    private BenefitEligibilityCacheAdapter cache;

    @BeforeEach
    void setUp() {
        cache = new BenefitEligibilityCacheAdapter(repository, 300_000L);
    }

    @Nested
    @DisplayName("refresh-interval validation (locked-down floor, spec-driven)")
    class RefreshIntervalValidation {

        @Test
        @DisplayName("accepts the default 5-minute interval")
        void acceptsDefault() {
            assertThat(new BenefitEligibilityCacheAdapter(repository, 300_000L)).isNotNull();
        }

        @Test
        @DisplayName("accepts exactly the 1-minute floor")
        void acceptsExactlyTheFloor() {
            assertThat(new BenefitEligibilityCacheAdapter(
                    repository, BenefitEligibilityCacheAdapter.MIN_REFRESH_INTERVAL_MS)).isNotNull();
        }

        @Test
        @DisplayName("rejects anything below the 1-minute floor — never silently clamps")
        void rejectsBelowFloor() {
            assertThatThrownBy(() -> new BenefitEligibilityCacheAdapter(
                    repository, BenefitEligibilityCacheAdapter.MIN_REFRESH_INTERVAL_MS - 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("refresh-interval-ms");
        }

        @Test
        @DisplayName("rejects zero and negative values")
        void rejectsZeroAndNegative() {
            assertThatThrownBy(() -> new BenefitEligibilityCacheAdapter(repository, 0L))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new BenefitEligibilityCacheAdapter(repository, -1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("before any load")
    class BeforeLoad {

        @Test
        @DisplayName("isLoaded is false and every lookup is empty (fail closed, spec §4.3)")
        void notLoadedYet() {
            assertThat(cache.isLoaded()).isFalse();
            assertThat(cache.minimumTierFor(BENEFIT_A)).isEmpty();
        }
    }

    @Nested
    @DisplayName("initial load")
    class InitialLoad {

        @Test
        @DisplayName("populates the snapshot from the repository")
        void populatesSnapshot() {
            when(repository.findAll()).thenReturn(List.of(
                    new BenefitEligibility(BENEFIT_A, CardTier.PLATINUM),
                    new BenefitEligibility(BENEFIT_B, CardTier.INFINITE)));

            cache.initialLoad();

            assertThat(cache.isLoaded()).isTrue();
            assertThat(cache.minimumTierFor(BENEFIT_A)).contains(CardTier.PLATINUM);
            assertThat(cache.minimumTierFor(BENEFIT_B)).contains(CardTier.INFINITE);
        }

        @Test
        @DisplayName("a repository failure is swallowed — isLoaded stays false, no exception propagates")
        void failureIsSwallowed() {
            when(repository.findAll()).thenThrow(new RuntimeException("db unreachable"));

            cache.initialLoad(); // must not throw

            assertThat(cache.isLoaded()).isFalse();
        }
    }

    @Nested
    @DisplayName("scheduled refresh")
    class ScheduledRefresh {

        @Test
        @DisplayName("a successful refresh replaces the snapshot")
        void refreshReplacesSnapshot() {
            when(repository.findAll())
                    .thenReturn(List.of(new BenefitEligibility(BENEFIT_A, CardTier.PLATINUM)))
                    .thenReturn(List.of(new BenefitEligibility(BENEFIT_A, CardTier.INFINITE)));

            cache.initialLoad();
            assertThat(cache.minimumTierFor(BENEFIT_A)).contains(CardTier.PLATINUM);

            cache.scheduledRefresh();
            assertThat(cache.minimumTierFor(BENEFIT_A)).contains(CardTier.INFINITE);
        }

        @Test
        @DisplayName("a failed refresh keeps serving the last known-good snapshot (spec §4.3)")
        void refreshFailureKeepsLastGoodSnapshot() {
            when(repository.findAll())
                    .thenReturn(List.of(new BenefitEligibility(BENEFIT_A, CardTier.SIGNATURE)))
                    .thenThrow(new RuntimeException("db unreachable"));

            cache.initialLoad();
            cache.scheduledRefresh(); // must not throw, must not clear the snapshot

            assertThat(cache.isLoaded()).isTrue();
            assertThat(cache.minimumTierFor(BENEFIT_A)).contains(CardTier.SIGNATURE);
        }
    }

    @Nested
    @DisplayName("concurrent refresh (spec §7) — exact parameters below, not left implicit")
    class ConcurrentRefresh {

        /** Reader concurrency. Chosen to exceed typical CI runner core counts (2-4),
         *  so at least some readers are genuinely contending rather than just
         *  round-robining on idle cores. */
        private static final int READER_THREADS = 8;

        /** Wall-clock budget for the whole race — long enough for many refresh
         *  cycles and a large read sample to accumulate, short enough that this
         *  test doesn't meaningfully slow the suite down. */
        private static final Duration TEST_DURATION = Duration.ofSeconds(2);

        /** Per-thread cap on retained latency samples. Reads keep happening for the
         *  full {@link #TEST_DURATION} regardless, but only up to this many samples
         *  per thread are kept for the p99 computation — bounds memory and the cost
         *  of sorting the sample set, since 8 threads doing sub-microsecond map
         *  lookups for 2 seconds would otherwise accumulate tens of millions of
         *  entries. */
        private static final int MAX_SAMPLES_PER_THREAD = 20_000;

        /** p99 read latency budget for a single {@code minimumTierFor} call under
         *  concurrent refresh. This measures only the in-memory lookup itself (no
         *  network, no serialization) — 10ms is deliberately generous versus the
         *  sub-microsecond cost such a call should normally have, to absorb JIT
         *  warmup and GC pauses on a loaded CI machine without becoming flaky, while
         *  still catching a real regression (e.g. a lock-based design replacing the
         *  current lock-free AtomicReference swap). */
        private static final Duration P99_LATENCY_BUDGET = Duration.ofMillis(10);

        @Test
        @DisplayName(READER_THREADS + " reader threads x " + "2s, p99 read latency < 10ms, "
                + "no torn/garbage reads during concurrent snapshot swaps")
        void noTornReadsAndBoundedLatencyDuringSwap() throws InterruptedException {
            // Two known-valid generations. The AtomicReference swap in loadOnce() means
            // every minimumTierFor() call sees a fully-formed map from exactly one
            // generation — never a partially-built one — because the map is built
            // completely (Collectors.toUnmodifiableMap) before the single snapshot.set()
            // call publishes it. This test exercises that under real concurrent load
            // rather than just asserting it from reading the code.
            CardTier gen1 = CardTier.PLATINUM;
            CardTier gen2 = CardTier.INFINITE;
            when(repository.findAll())
                    .thenReturn(List.of(new BenefitEligibility(BENEFIT_A, gen1)));
            cache.initialLoad();

            AtomicBoolean stop = new AtomicBoolean(false);
            AtomicBoolean sawUnexpectedValue = new AtomicBoolean(false);
            AtomicInteger readCount = new AtomicInteger(0);
            AtomicInteger refreshCount = new AtomicInteger(0);
            Set<CardTier> validValues = Set.of(gen1, gen2);
            List<Queue<Long>> latencySamplesByThread = new ArrayList<>();
            for (int i = 0; i < READER_THREADS; i++) {
                latencySamplesByThread.add(new ConcurrentLinkedQueue<>());
            }

            ExecutorService pool = Executors.newFixedThreadPool(READER_THREADS + 1);
            CountDownLatch readersDone = new CountDownLatch(READER_THREADS);
            long deadlineNanos = System.nanoTime() + TEST_DURATION.toNanos();
            try {
                for (int t = 0; t < READER_THREADS; t++) {
                    Queue<Long> samples = latencySamplesByThread.get(t);
                    pool.submit(() -> {
                        try {
                            while (!stop.get()) {
                                long start = System.nanoTime();
                                Optional<CardTier> value = cache.minimumTierFor(BENEFIT_A);
                                long elapsed = System.nanoTime() - start;
                                readCount.incrementAndGet();
                                if (samples.size() < MAX_SAMPLES_PER_THREAD) {
                                    samples.add(elapsed);
                                }
                                // A "torn" or corrupted read would show up as either an
                                // empty result (impossible once loaded — every generation
                                // here always contains BENEFIT_A) or a value outside the
                                // two known-valid generations.
                                if (value.isEmpty() || !validValues.contains(value.get())) {
                                    sawUnexpectedValue.set(true);
                                }
                            }
                        } finally {
                            readersDone.countDown();
                        }
                    });
                }

                // Refresh as fast as possible (no pacing sleep) for the same duration,
                // to maximise contention against the readers within the time budget.
                // Alternates the mocked repository response between the two generations.
                int i = 0;
                while (System.nanoTime() < deadlineNanos) {
                    CardTier next = (i % 2 == 0) ? gen2 : gen1;
                    when(repository.findAll())
                            .thenReturn(List.of(new BenefitEligibility(BENEFIT_A, next)));
                    cache.scheduledRefresh();
                    refreshCount.incrementAndGet();
                    i++;
                }
            } finally {
                stop.set(true);
                readersDone.await(5, TimeUnit.SECONDS);
                pool.shutdownNow();
            }

            assertThat(sawUnexpectedValue).as("no reader ever saw a torn/garbage value").isFalse();
            assertThat(readCount.get()).as("readers actually raced against the refreshes").isPositive();
            assertThat(refreshCount.get()).as("refreshes actually happened during the read race").isPositive();

            List<Long> allSamples = latencySamplesByThread.stream()
                    .flatMap(Queue::stream)
                    .sorted()
                    .toList();
            assertThat(allSamples).as("latency samples were actually collected").isNotEmpty();
            long p99Nanos = allSamples.get((int) (allSamples.size() * 0.99));
            assertThat(p99Nanos)
                    .as("p99 read latency (%d samples) must stay under %s during concurrent refresh",
                            allSamples.size(), P99_LATENCY_BUDGET)
                    .isLessThan(P99_LATENCY_BUDGET.toNanos());
        }
    }
}
