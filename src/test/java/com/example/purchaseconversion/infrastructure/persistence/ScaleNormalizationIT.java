package com.example.purchaseconversion.infrastructure.persistence;

import com.example.purchaseconversion.application.port.out.ExchangeRateHotCachePort;
import com.example.purchaseconversion.application.port.out.ExchangeRateRepositoryPort;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.domain.ExchangeRate;
import com.example.purchaseconversion.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G4-P0-3 — scale-6 normalisation end-to-end. Treasury publishes variable scale
 * (1, 2, 3, 4 observed), and an outlier high-precision case can also appear.
 * Persistence + re-read must yield exactly scale-6 BigDecimal.
 */
@SpringBootTest
class ScaleNormalizationIT extends AbstractPostgresIT {

    private static final CurrencyDescriptor CAD = CurrencyDescriptor.of("Canada-Dollar");

    @Autowired
    private ExchangeRateRepositoryPort repo;

    @Autowired
    private ExchangeRateHotCachePort hotCache;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void clean() {
        jdbcClient.sql("DELETE FROM exchange_rates").update();
    }

    @ParameterizedTest(name = "input={0} → persisted+re-read scale=6 value={1}")
    @CsvSource({
            // Treasury variants observed in the Phase-3 prototype
            "148.0,      148.000000",
            "1.37,       1.370000",
            "1.393,      1.393000",
            "0.085,      0.085000",
            "159.41,     159.410000",
            "159.4100,   159.410000",
            // High-precision inbound — value > scale 6 is rounded HALF_UP
            "1.1234565,  1.123457",
            // Below scale 6 — padded with zeros
            "100,        100.000000"
    })
    @DisplayName("G4-P0-3 — scale-6 normalisation across Treasury variants")
    void normalisesToScale6(String inputRate, String expectedScale6) {
        ExchangeRate fresh = new ExchangeRate(
                CAD,
                LocalDate.of(2026, 4, 15),
                LocalDate.of(2026, 4, 15),
                new BigDecimal(inputRate));
        repo.upsertVersioned(List.of(fresh));

        List<ExchangeRate> readBack = repo.findInWindow(
                CAD, LocalDate.of(2026, 4, 15), LocalDate.of(2026, 4, 15));

        assertThat(readBack).hasSize(1);
        BigDecimal persisted = readBack.get(0).rate();
        assertThat(persisted.scale()).isEqualTo(6);
        assertThat(persisted).isEqualByComparingTo(new BigDecimal(expectedScale6));
    }
}
