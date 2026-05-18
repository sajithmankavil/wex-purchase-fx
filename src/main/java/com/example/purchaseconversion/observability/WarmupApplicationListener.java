package com.example.purchaseconversion.observability;

import com.example.purchaseconversion.application.port.out.CurrencyAliasPort;
import com.example.purchaseconversion.application.port.out.TreasuryClientPort;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Warm-up job per `docs/operations/observability.md §8` + C2 prompt §S1.
 *
 * <p>On {@link ApplicationReadyEvent}, asynchronously pre-fetches Treasury rates
 * for the top-10 currencies at the latest quarter-end record date so the first
 * inbound conversion request hits a warm hot-cache + DB. Implementation invariants:
 *
 * <ul>
 *   <li><b>Non-blocking on readiness.</b> Spring's readiness probe flips UP
 *       before this listener returns — the work runs on a separate executor.</li>
 *   <li><b>Idempotent.</b> Repeated invocations (e.g., on context refresh) are
 *       no-ops at the persistence layer thanks to the versioned-upsert contract
 *       in {@code ExchangeRateRepositoryPort}.</li>
 *   <li><b>Failure-tolerant.</b> Per-currency failures are logged at WARN and
 *       do not propagate; warm-up is best-effort and cannot block service start.</li>
 * </ul>
 */
@Component
public class WarmupApplicationListener {

    private static final Logger LOG = LoggerFactory.getLogger(WarmupApplicationListener.class);

    /** Top-10 currencies — match the v1 alias-table entries (ADR-0001 D-8). */
    private static final List<String> TOP_10 = List.of(
            "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "INR", "United States-Dollar");

    private final TreasuryClientPort treasuryClient;
    private final CurrencyAliasPort aliasPort;
    private final Executor executor;
    private final boolean enabled;

    public WarmupApplicationListener(
            TreasuryClientPort treasuryClient,
            CurrencyAliasPort aliasPort,
            @Value("${wex.warmup.enabled:true}") boolean enabled) {
        this.treasuryClient = Objects.requireNonNull(treasuryClient, "treasuryClient must not be null");
        this.aliasPort = Objects.requireNonNull(aliasPort, "aliasPort must not be null");
        this.executor = ForkJoinPool.commonPool();
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!enabled) {
            LOG.info("warmup.skipped reason=disabled");
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate windowUpper = today;
        LocalDate windowLower = today.minusMonths(6);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        for (String input : TOP_10) {
            CompletableFuture.runAsync(() -> warmOne(input, windowLower, windowUpper, ok, fail), executor);
        }
        // Don't wait — warm-up is fire-and-forget; readiness stays UP.
        LOG.info("warmup.started count={}", TOP_10.size());
    }

    /** Visible for tests — synchronous variant. */
    void warmOne(String input, LocalDate lower, LocalDate upper, AtomicInteger ok, AtomicInteger fail) {
        try {
            Optional<CurrencyDescriptor> resolved = aliasPort.resolve(input);
            if (resolved.isEmpty()) {
                LOG.warn("warmup.alias_miss input={}", input);
                fail.incrementAndGet();
                return;
            }
            treasuryClient.fetchRates(resolved.get(), lower, upper);
            ok.incrementAndGet();
            LOG.debug("warmup.success currency={}", resolved.get().value());
        } catch (RuntimeException e) {
            fail.incrementAndGet();
            LOG.warn("warmup.failure currency={} errClass={}",
                    input, e.getClass().getSimpleName());
        }
    }

    /** Visible for tests — exposes the configured currency list. */
    static List<String> warmUpCurrencies() {
        return TOP_10;
    }
}
