package com.example.purchaseconversion.infrastructure.treasury;

import com.example.purchaseconversion.application.exception.UpstreamUnavailableException;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G4-P1-23 — graceful shutdown semantics for {@link SingleFlightGate}. On
 * {@link ConfigurableApplicationContext#close()}, the gate's
 * {@code @PreDestroy} hook runs {@link SingleFlightGate#releaseAll()}; any
 * in-flight loser exits immediately with {@code UpstreamUnavailableException("shutdown")}.
 */
@SpringBootTest
class GracefulShutdownIT extends AbstractPostgresIT {

    private static final CurrencyDescriptor CAD = CurrencyDescriptor.of("Canada-Dollar");

    @Autowired
    private SingleFlightGate gate;

    @Autowired
    private ConfigurableApplicationContext context;

    @Test
    @DisplayName("G4-P1-23 — context close triggers releaseAll; pending loser fails fast")
    void contextCloseReleasesLosers() throws Exception {
        SingleFlightGate.Key key = SingleFlightGate.Key.forTransactionDate(CAD, LocalDate.of(2026, 5, 1));
        CountDownLatch winnerHolds = new CountDownLatch(1);

        ExecutorService exec = Executors.newFixedThreadPool(2);
        try {
            // Winner enters the gate and stays.
            Future<?> winner = exec.submit(() -> {
                try {
                    return gate.runOnce(key, () -> {
                        try { winnerHolds.await(5, TimeUnit.SECONDS); }
                        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        return null;
                    }, () -> false);
                } catch (UpstreamUnavailableException e) {
                    // Acceptable — releaseAll may flip our outcome to "shutdown" too.
                    return null;
                }
            });
            Thread.sleep(50);

            // Loser starts waiting.
            Future<UpstreamUnavailableException> loser = exec.submit(() -> {
                try {
                    gate.runOnce(key, () -> null, () -> false);
                    return null;
                } catch (UpstreamUnavailableException e) {
                    return e;
                }
            });
            Thread.sleep(100);

            // Closing the context invokes releaseAll() via the @PreDestroy hook.
            // (We re-open it after — Spring Test caches contexts so subsequent tests
            // would rebuild; for this IT a single test is sufficient.)
            context.close();

            UpstreamUnavailableException ex = loser.get(3, TimeUnit.SECONDS);
            assertThat(ex).isNotNull();
            assertThat(ex.getReason()).isEqualTo("shutdown");

            winnerHolds.countDown();
            winner.get(2, TimeUnit.SECONDS);
        } finally {
            exec.shutdownNow();
        }
    }
}
