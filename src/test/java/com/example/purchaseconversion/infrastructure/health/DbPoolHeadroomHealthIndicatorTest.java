package com.example.purchaseconversion.infrastructure.health;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * G6-P0-4 — readiness DOWN when {@code active >= max - 1} sustained for ≥ 5 s.
 *
 * <p>Drives the indicator with a controllable {@link MutableClock}, sequencing the
 * pool MX-bean's {@code active} count across saturation windows.
 */
class DbPoolHeadroomHealthIndicatorTest {

    private static final int MAX = 20;
    private static final Instant T0 = Instant.parse("2026-05-17T12:00:00Z");

    @Test
    @DisplayName("UP when pool has headroom")
    void upWhenHeadroom() {
        MutableClock clock = new MutableClock(T0);
        HikariDataSource ds = mockedHikari(5);
        DbPoolHeadroomHealthIndicator indicator = new DbPoolHeadroomHealthIndicator(ds, clock, 5000);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("active", 5).containsEntry("max", MAX);
    }

    @Test
    @DisplayName("UP within the first 4.99 s of saturation")
    void upInSustainedWindow() {
        MutableClock clock = new MutableClock(T0);
        HikariDataSource ds = mockedHikari(MAX - 1);
        DbPoolHeadroomHealthIndicator indicator = new DbPoolHeadroomHealthIndicator(ds, clock, 5000);

        // 1st reading at T0 — starts the saturation window
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);

        // 2nd reading at T0 + 4.999 s — still inside the window
        clock.advance(Duration.ofMillis(4999));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("DOWN once saturation has held for the configured window")
    void downAfterSustainedSaturation() {
        MutableClock clock = new MutableClock(T0);
        HikariDataSource ds = mockedHikari(MAX - 1);
        DbPoolHeadroomHealthIndicator indicator = new DbPoolHeadroomHealthIndicator(ds, clock, 5000);

        indicator.health();                          // starts the window
        clock.advance(Duration.ofMillis(5001));
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "db_pool_sustained_saturation");
    }

    @Test
    @DisplayName("recovery — saturation drops below threshold resets the window")
    void recoveryResetsWindow() {
        MutableClock clock = new MutableClock(T0);
        AtomicReference<Integer> activeRef = new AtomicReference<>(MAX - 1);
        HikariDataSource ds = mockedHikariFrom(activeRef);
        DbPoolHeadroomHealthIndicator indicator = new DbPoolHeadroomHealthIndicator(ds, clock, 5000);

        // Saturated → window opens
        indicator.health();
        clock.advance(Duration.ofMillis(4000));

        // Drops below threshold → window resets
        activeRef.set(5);
        Health midRecovery = indicator.health();
        assertThat(midRecovery.getStatus()).isEqualTo(Status.UP);

        // Saturates again — must run the full 5 s again before DOWN
        activeRef.set(MAX - 1);
        clock.advance(Duration.ofMillis(4000));
        Health stillUp = indicator.health();
        assertThat(stillUp.getStatus()).isEqualTo(Status.UP);

        // Need to cross the 5000ms threshold from the NEW window-start (set at the
        // previous health() call when re-saturation was observed). Total wall-time
        // since re-saturation: 5100 ms > 5000 ms threshold -> DOWN.
        clock.advance(Duration.ofMillis(5100));
        Health nowDown = indicator.health();
        assertThat(nowDown.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("rejects non-Hikari DataSource implementations")
    void rejectsNonHikari() {
        DataSource notHikari = mock(DataSource.class);
        assertThatThrownBy(() -> new DbPoolHeadroomHealthIndicator(notHikari, Clock.systemUTC(), 5000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HikariDataSource");
    }

    private HikariDataSource mockedHikari(int active) {
        return mockedHikariFrom(new AtomicReference<>(active));
    }

    private HikariDataSource mockedHikariFrom(AtomicReference<Integer> activeRef) {
        HikariDataSource ds = mock(HikariDataSource.class);
        HikariPoolMXBean mx = mock(HikariPoolMXBean.class);
        when(ds.getMaximumPoolSize()).thenReturn(MAX);
        when(ds.getPoolName()).thenReturn("wex-pool");
        when(ds.getHikariPoolMXBean()).thenReturn(mx);
        when(mx.getActiveConnections()).thenAnswer(inv -> activeRef.get());
        when(mx.getIdleConnections()).thenAnswer(inv -> MAX - activeRef.get());
        return ds;
    }

    /** Test clock that advances by an explicit duration. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(java.time.ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }
}
