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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
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
        cache = new BenefitEligibilityCacheAdapter(repository);
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
    @DisplayName("concurrent refresh (spec §7)")
    class ConcurrentRefresh {

        @Test
        @DisplayName("readers never observe a torn or garbage value while a refresh swaps the snapshot")
        void noTornReadsDuringSwap() throws InterruptedException {
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

            int readerThreads = 8;
            int refreshCycles = 200;
            AtomicBoolean stop = new AtomicBoolean(false);
            AtomicBoolean sawUnexpectedValue = new AtomicBoolean(false);
            AtomicInteger readCount = new AtomicInteger(0);
            Set<CardTier> validValues = Set.of(gen1, gen2);

            ExecutorService pool = Executors.newFixedThreadPool(readerThreads + 1);
            CountDownLatch readersDone = new CountDownLatch(readerThreads);
            try {
                for (int i = 0; i < readerThreads; i++) {
                    pool.submit(() -> {
                        try {
                            while (!stop.get()) {
                                Optional<CardTier> value = cache.minimumTierFor(BENEFIT_A);
                                readCount.incrementAndGet();
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

                // Alternate the mocked repository response between the two generations
                // and refresh repeatedly while readers are hammering the cache.
                for (int i = 0; i < refreshCycles; i++) {
                    CardTier next = (i % 2 == 0) ? gen2 : gen1;
                    when(repository.findAll())
                            .thenReturn(List.of(new BenefitEligibility(BENEFIT_A, next)));
                    cache.scheduledRefresh();
                }
            } finally {
                stop.set(true);
                readersDone.await(5, TimeUnit.SECONDS);
                pool.shutdownNow();
            }

            assertThat(sawUnexpectedValue).as("no reader ever saw a torn/garbage value").isFalse();
            assertThat(readCount.get()).as("readers actually raced against the refreshes").isPositive();
        }
    }
}
