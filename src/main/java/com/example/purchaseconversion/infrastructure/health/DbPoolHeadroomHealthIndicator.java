package com.example.purchaseconversion.infrastructure.health;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Readiness signal for the HikariCP pool (component-design.md §3.4; G6-P0-4 / G4-P1-19).
 *
 * <p>The pool is considered <b>at-capacity</b> when {@code active >= max - 1}. Once
 * it stays at-capacity for at least {@code sustainedSaturationMs} milliseconds the
 * indicator reports DOWN; the load balancer stops routing requests to this replica
 * (Spring Boot's readiness state group routes this indicator through {@code /readiness}).
 *
 * <p>Sustained-window state is held in an atomic reference so the contributor stays
 * stateless across Spring's health-cache eviction.
 */
@Component
public class DbPoolHeadroomHealthIndicator implements HealthIndicator {

    private final HikariDataSource hikari;
    private final Clock clock;
    private final long sustainedSaturationMillis;

    /** When non-null, the instant at which saturation was first observed in the current window. */
    private final AtomicReference<Instant> saturationStart = new AtomicReference<>();

    public DbPoolHeadroomHealthIndicator(
            DataSource dataSource,
            Clock clock,
            @Value("${wex.readiness.db-pool-headroom.sustained-saturation-ms:5000}") long sustainedSaturationMillis) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        if (!(dataSource instanceof HikariDataSource)) {
            throw new IllegalStateException(
                    "DbPoolHeadroomHealthIndicator requires HikariDataSource; got "
                            + dataSource.getClass().getName());
        }
        this.hikari = (HikariDataSource) dataSource;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (sustainedSaturationMillis < 0) {
            throw new IllegalArgumentException(
                    "sustainedSaturationMillis must be >= 0; got " + sustainedSaturationMillis);
        }
        this.sustainedSaturationMillis = sustainedSaturationMillis;
    }

    @Override
    public Health health() {
        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        int max = hikari.getMaximumPoolSize();
        int active = pool == null ? 0 : pool.getActiveConnections();
        int idle = pool == null ? 0 : pool.getIdleConnections();
        boolean saturated = active >= max - 1;

        Instant now = clock.instant();
        long elapsed = updateSaturationWindow(saturated, now);

        Health.Builder builder = Health.up()
                .withDetail("pool", hikari.getPoolName())
                .withDetail("active", active)
                .withDetail("idle", idle)
                .withDetail("max", max)
                .withDetail("sustainedSaturationMillis", elapsed);

        if (saturated && elapsed >= sustainedSaturationMillis) {
            return builder.down()
                    .withDetail("reason", "db_pool_sustained_saturation")
                    .build();
        }
        return builder.build();
    }

    /**
     * Updates the saturation-start atomic. Returns the elapsed sustained-saturation
     * window in millis; 0 when not currently saturated.
     */
    private long updateSaturationWindow(boolean saturated, Instant now) {
        if (!saturated) {
            saturationStart.set(null);
            return 0L;
        }
        Instant start = saturationStart.updateAndGet(prev -> prev == null ? now : prev);
        return java.time.Duration.between(start, now).toMillis();
    }
}
