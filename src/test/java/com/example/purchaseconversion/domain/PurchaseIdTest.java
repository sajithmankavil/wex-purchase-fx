package com.example.purchaseconversion.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseIdTest {

    @Test
    @DisplayName("next() generates a UUID v7")
    void nextGeneratesV7() {
        PurchaseId id = PurchaseId.next();
        assertThat(id.value().version()).isEqualTo(7);
    }

    @RepeatedTest(20)
    @DisplayName("two consecutive next() calls produce distinct ids")
    void nextProducesDistinctIds() {
        PurchaseId a = PurchaseId.next();
        PurchaseId b = PurchaseId.next();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("100 next() calls produce 100 distinct ids")
    void nextProduces100DistinctIds() {
        Set<PurchaseId> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            assertThat(seen.add(PurchaseId.next())).as("duplicate at iteration %d", i).isTrue();
        }
    }

    @Test
    @DisplayName("next() ids are monotonic on the time-portion within the same millisecond bucket")
    void nextIdsAreTimeOrdered() throws InterruptedException {
        // UUID v7 layout: first 48 bits are unix milliseconds (RFC 9562 §5.7).
        PurchaseId first = PurchaseId.next();
        Thread.sleep(2);
        PurchaseId later = PurchaseId.next();
        long firstMs = extractV7TimestampMs(first.value());
        long laterMs = extractV7TimestampMs(later.value());
        assertThat(laterMs).isGreaterThanOrEqualTo(firstMs);
    }

    @Test
    @DisplayName("fromString parses a canonical UUID v7 string")
    void fromStringParsesCanonical() {
        PurchaseId generated = PurchaseId.next();
        PurchaseId parsed = PurchaseId.fromString(generated.toString());
        assertThat(parsed).isEqualTo(generated);
        assertThat(parsed.value().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("fromString rejects malformed UUID strings")
    void fromStringRejectsMalformed() {
        assertThatThrownBy(() -> PurchaseId.fromString("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PurchaseId.fromString(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fromString rejects a syntactically valid non-v7 UUID")
    void fromStringRejectsNonV7() {
        // A UUID v4 (random) — UUID.randomUUID() produces v4.
        UUID v4 = UUID.randomUUID();
        assertThat(v4.version()).isEqualTo(4);
        assertThatThrownBy(() -> PurchaseId.fromString(v4.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v7");
    }

    @Test
    @DisplayName("constructor rejects null")
    void rejectsNull() {
        assertThatThrownBy(() -> new PurchaseId(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PurchaseId.fromString(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("toString returns the canonical UUID string")
    void toStringIsCanonical() {
        PurchaseId id = PurchaseId.next();
        assertThat(id.toString()).matches("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    /**
     * RFC 9562 §5.7: the first 48 bits of a UUID v7 are the Unix-epoch millisecond timestamp.
     * Extract them from the {@link UUID#getMostSignificantBits()} layout.
     */
    private static long extractV7TimestampMs(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }
}
