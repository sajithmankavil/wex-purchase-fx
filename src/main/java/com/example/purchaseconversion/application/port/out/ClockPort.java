package com.example.purchaseconversion.application.port.out;

import java.time.LocalDate;

/**
 * Outbound port abstracting "today" so application services are deterministically testable
 * with fixed clocks (component-design.md §7 "Time"; AC-006 / FR-001 future-date check).
 *
 * <p>Implemented by a Spring bean in {@code config} (Chunk B / C) wrapping
 * {@link java.time.Clock}. Defaulted to UTC per the cross-cutting time convention.
 */
public interface ClockPort {

    /**
     * Returns today's date in UTC. Tests substitute a fixed-date implementation.
     */
    LocalDate today();
}
