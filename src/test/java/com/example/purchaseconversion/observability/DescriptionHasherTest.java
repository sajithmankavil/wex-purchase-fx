package com.example.purchaseconversion.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DescriptionHasherTest {

    @Nested
    @DisplayName("Profile-aware key resolution")
    class KeyResolution {

        @Test
        @DisplayName("dev profile without env key falls back to v0 dev key")
        void devFallback() {
            DescriptionHasher h = new DescriptionHasher("dev", "", "v1");
            assertThat(h.keyVersion()).isEqualTo("v0");
        }

        @Test
        @DisplayName("ADR-0001 D-12 — prod profile without env key refuses to start")
        void prodRefusesToStart() {
            assertThatThrownBy(() -> new DescriptionHasher("prod", "", "v1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("WEX_LOG_HASH_KEY");
        }

        @Test
        @DisplayName("prod profile with env key uses the configured version prefix")
        void prodWithEnvKey() {
            DescriptionHasher h = new DescriptionHasher("prod", "some-secret-key", "v2");
            assertThat(h.keyVersion()).isEqualTo("v2");
        }
    }

    @Nested
    @DisplayName("HMAC output shape")
    class HashOutput {

        @Test
        @DisplayName("non-empty input returns vN:<64-hex> for SHA-256")
        void shapeForNonEmpty() {
            DescriptionHasher h = new DescriptionHasher("test", "", "v1");
            String hashed = h.hash("Coffee");
            assertThat(hashed).matches("v0:[0-9a-f]{64}");
        }

        @Test
        @DisplayName("null + empty inputs return the empty string")
        void emptyForNullBlank() {
            DescriptionHasher h = new DescriptionHasher("test", "", "v1");
            assertThat(h.hash(null)).isEmpty();
            assertThat(h.hash("")).isEmpty();
        }

        @Test
        @DisplayName("same input + same key → same hash")
        void deterministic() {
            DescriptionHasher h = new DescriptionHasher("test", "", "v1");
            assertThat(h.hash("Coffee")).isEqualTo(h.hash("Coffee"));
        }

        @Test
        @DisplayName("different keys → different hashes for same input")
        void keyVersionedDistinguishability() {
            DescriptionHasher h1 = new DescriptionHasher("prod", "key-version-1", "v1");
            DescriptionHasher h2 = new DescriptionHasher("prod", "key-version-2", "v1");
            assertThat(h1.hash("Coffee")).isNotEqualTo(h2.hash("Coffee"));
        }
    }
}
