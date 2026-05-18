package com.example.purchaseconversion.config;

import com.example.purchaseconversion.application.conversion.ConversionService;
import com.example.purchaseconversion.application.port.in.ConvertPurchaseUseCase;
import com.example.purchaseconversion.application.port.in.RegisterPurchaseUseCase;
import com.example.purchaseconversion.application.port.in.RetrievePurchaseUseCase;
import com.example.purchaseconversion.application.port.out.ClockPort;
import com.example.purchaseconversion.application.port.out.CurrencyAliasPort;
import com.example.purchaseconversion.application.port.out.ExchangeRateHotCachePort;
import com.example.purchaseconversion.application.port.out.ExchangeRateRepositoryPort;
import com.example.purchaseconversion.application.port.out.PurchaseRepositoryPort;
import com.example.purchaseconversion.application.port.out.TreasuryClientPort;
import com.example.purchaseconversion.application.purchase.PurchaseService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Wires application services (pure POJOs from A2) into the Spring context.
 *
 * <p>This keeps application/* free of Spring annotations (ArchUnit-enforced by
 * {@code noSpringStereotypesInApplication} from A2). All bean wiring is here.
 *
 * <p>Note: a stub {@link TreasuryClientPort} is wired by {@link StubTreasuryClient}
 * for B1 (no real upstream HTTP yet). B2 replaces it with the live
 * {@code TreasuryClientAdapter}. The stub returns an empty list — the
 * conversion flow's "after Treasury fetch + DB re-read" branch ends in a
 * {@code ConversionRateNotAvailableException} when local rates are exhausted.
 */
@Configuration
public class WexConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public ClockPort clockPort(Clock clock) {
        return new SystemClockPort(clock);
    }

    @Bean
    public JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    @Bean
    @Primary
    public RegisterPurchaseUseCase registerPurchaseUseCase(PurchaseService service) {
        return service;
    }

    @Bean
    @Primary
    public RetrievePurchaseUseCase retrievePurchaseUseCase(PurchaseService service) {
        return service;
    }

    @Bean
    public PurchaseService purchaseService(PurchaseRepositoryPort purchaseRepository, ClockPort clock) {
        return new PurchaseService(purchaseRepository, clock);
    }

    @Bean
    public ConvertPurchaseUseCase convertPurchaseUseCase(
            PurchaseRepositoryPort purchaseRepository,
            ExchangeRateRepositoryPort exchangeRateRepository,
            ExchangeRateHotCachePort hotCache,
            TreasuryClientPort treasuryClient,
            CurrencyAliasPort aliasPort) {
        return new ConversionService(
                purchaseRepository, exchangeRateRepository, hotCache, treasuryClient, aliasPort);
    }

    /**
     * B1 placeholder: returns empty list. B2 replaces this with the
     * Resilience4j-wrapped TreasuryClientAdapter.
     */
    @Bean
    public TreasuryClientPort treasuryClientPort() {
        return new StubTreasuryClient();
    }

    private static final class SystemClockPort implements ClockPort {
        private final Clock clock;

        private SystemClockPort(Clock clock) {
            this.clock = clock;
        }

        @Override
        public LocalDate today() {
            return LocalDate.now(clock.withZone(ZoneOffset.UTC));
        }
    }

    private static final class StubTreasuryClient implements TreasuryClientPort {
        @Override
        public java.util.List<com.example.purchaseconversion.domain.ExchangeRate> fetchRates(
                com.example.purchaseconversion.domain.CurrencyDescriptor currency,
                LocalDate windowLower,
                LocalDate windowUpper) {
            return java.util.List.of();
        }
    }
}
