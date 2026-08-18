package com.example.purchaseconversion.config;

import com.example.purchaseconversion.application.conversion.ConversionService;
import com.example.purchaseconversion.application.eligibility.EligibilityService;
import com.example.purchaseconversion.application.port.in.CheckEligibilityUseCase;
import com.example.purchaseconversion.application.port.in.ConvertPurchaseUseCase;
import com.example.purchaseconversion.application.port.out.BenefitEligibilityCachePort;
import com.example.purchaseconversion.application.port.out.ClockPort;
import com.example.purchaseconversion.application.port.out.CurrencyAliasPort;
import com.example.purchaseconversion.application.port.out.ExchangeRateHotCachePort;
import com.example.purchaseconversion.application.port.out.ExchangeRateRepositoryPort;
import com.example.purchaseconversion.application.port.out.PurchaseRepositoryPort;
import com.example.purchaseconversion.application.port.out.TreasuryClientPort;
import com.example.purchaseconversion.application.purchase.PurchaseService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Wires application services (pure POJOs from A2) into the Spring context.
 *
 * <p>This keeps application/* free of Spring annotations (ArchUnit-enforced by
 * {@code noSpringStereotypesInApplication} from A2). All bean wiring is here.
 *
 * <p>The B1 stub TreasuryClientPort is removed in B2 — the live
 * {@code TreasuryClientAdapter} is now component-scanned. RestClient.Builder
 * is exposed here with a sane request-factory timeout so the adapter does not
 * need to construct one inline.
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

    // The single `purchaseService` bean implements both RegisterPurchaseUseCase and
    // RetrievePurchaseUseCase (see PurchaseService class). Two extra @Bean @Primary
    // factories previously aliased the same instance under interface-named bean
    // names — that registered THREE beans qualifying for each use-case interface,
    // all marked @Primary, which broke Spring's primary-bean-selection rule
    // (NoUniqueBeanDefinitionException at controller wiring). Removed the aliases;
    // Spring resolves by type via the single bean below.
    @Bean
    public PurchaseService purchaseService(PurchaseRepositoryPort purchaseRepository, ClockPort clock) {
        return new PurchaseService(purchaseRepository, clock);
    }

    @Bean
    public CheckEligibilityUseCase checkEligibilityUseCase(BenefitEligibilityCachePort cache) {
        return new EligibilityService(cache);
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
     * RestClient.Builder with a basic timeout request factory. The
     * Resilience4j @TimeLimiter is not applied at the adapter level (the
     * adapter is synchronous); per-call timeouts come from the request factory
     * configured here. Treasury timeout budget: 2 s connect + 2 s read.
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(2).toMillis());
        return RestClient.builder().requestFactory(factory);
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
}
