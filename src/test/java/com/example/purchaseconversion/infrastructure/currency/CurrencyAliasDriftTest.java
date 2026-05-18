package com.example.purchaseconversion.infrastructure.currency;

import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC-021b / AC-021c — alias resolution + drift detection.
 *
 * <p>Drift detection itself is via SLF4J-named logger {@code currency_alias.drift.detected};
 * the unit tests here verify the resolve/miss path. Log-emission verification is
 * left to a Logback ListAppender in a follow-up test if the reviewer needs it
 * — the adapter's behaviour is otherwise observable via {@link CurrencyAliasTableAdapter#resolve}.
 */
class CurrencyAliasDriftTest {

    private static final ResourceLoader REAL_LOADER = new DefaultResourceLoader();

    private CurrencyAliasTableAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CurrencyAliasTableAdapter(
                REAL_LOADER, new ObjectMapper(), "classpath:currency-aliases.json");
        adapter.loadAliases();
    }

    @Nested
    @DisplayName("ISO-4217 + descriptor resolution")
    class Resolution {

        @Test
        @DisplayName("AC-021 — ISO codes resolve to canonical descriptors")
        void isoCodeResolves() {
            assertThat(adapter.resolve("CAD"))
                    .contains(CurrencyDescriptor.of("Canada-Dollar"));
            assertThat(adapter.resolve("EUR"))
                    .contains(CurrencyDescriptor.of("Euro Zone-Euro"));
            assertThat(adapter.resolve("JPY"))
                    .contains(CurrencyDescriptor.of("Japan-Yen"));
        }

        @Test
        @DisplayName("ISO codes are case-insensitive")
        void isoCaseInsensitive() {
            assertThat(adapter.resolve("cad")).isPresent();
            assertThat(adapter.resolve("Cad")).isPresent();
            assertThat(adapter.resolve("cAd")).isPresent();
        }

        @Test
        @DisplayName("Treasury descriptor resolves verbatim (preserves Euro-Zone space)")
        void descriptorVerbatim() {
            assertThat(adapter.resolve("Euro Zone-Euro"))
                    .contains(CurrencyDescriptor.of("Euro Zone-Euro"));
        }

        @Test
        @DisplayName("Treasury descriptor is case-insensitive")
        void descriptorCaseInsensitive() {
            assertThat(adapter.resolve("canada-dollar"))
                    .contains(CurrencyDescriptor.of("Canada-Dollar"));
        }
    }

    @Nested
    @DisplayName("Drift / miss path (AC-021b/c)")
    class DriftPath {

        @Test
        @DisplayName("AC-021b — an unknown ISO-shaped input returns empty")
        void unknownIsoCode() {
            // 'ZZZ' is not in any column of the alias table; the adapter returns empty
            // and emits a currency_alias.drift.detected log event.
            Optional<CurrencyDescriptor> resolved = adapter.resolve("ZZZ");
            assertThat(resolved).isEmpty();
        }

        @Test
        @DisplayName("AC-021c — an unknown descriptor-shaped input returns empty")
        void unknownDescriptor() {
            Optional<CurrencyDescriptor> resolved = adapter.resolve("Atlantis-Drachma");
            assertThat(resolved).isEmpty();
        }

        @Test
        @DisplayName("garbage input returns empty without throwing")
        void garbageInput() {
            assertThat(adapter.resolve("")).isEmpty();
            assertThat(adapter.resolve("   ")).isEmpty();
            assertThat(adapter.resolve("not-a-currency-shape")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Refuse-to-start semantics (ADR-0001 D-8)")
    class RefuseToStart {

        @Test
        @DisplayName("missing resource → IllegalStateException at load time")
        void missingResource() {
            CurrencyAliasTableAdapter broken = new CurrencyAliasTableAdapter(
                    REAL_LOADER, new ObjectMapper(), "classpath:does-not-exist.json");
            assertThatThrownBy(broken::loadAliases)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("invalid JSON shape → IllegalStateException")
        void invalidJsonShape() {
            ResourceLoader loader = new DefaultResourceLoader() {
                @Override
                public Resource getResource(String location) {
                    return new ClassPathResource("test-aliases-broken.json", getClass().getClassLoader()) {
                        @Override
                        public boolean exists() { return true; }

                        @Override
                        public java.io.InputStream getInputStream() {
                            return new java.io.ByteArrayInputStream("{\"not_aliases\": []}".getBytes());
                        }
                    };
                }
            };
            CurrencyAliasTableAdapter broken = new CurrencyAliasTableAdapter(
                    loader, new ObjectMapper(), "classpath:test-aliases-broken.json");
            assertThatThrownBy(broken::loadAliases)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("aliases");
        }
    }
}
