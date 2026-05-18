package com.example.purchaseconversion.infrastructure;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for B1 ITs that need a real Postgres against which to run Liquibase.
 *
 * <p>Spins up a single container per test class (Testcontainers default), and binds
 * the DataSource via {@link DynamicPropertyRegistry} so Liquibase + JdbcClient pick
 * it up at @SpringBootTest startup. Tests that need file-mode H2 instead (e.g.
 * {@code DurabilityRestartIT}) do NOT extend this class.
 */
@Testcontainers
public abstract class AbstractPostgresIT {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("wex")
                    .withUsername("wex")
                    .withPassword("wex");

    @DynamicPropertySource
    static void bindDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
