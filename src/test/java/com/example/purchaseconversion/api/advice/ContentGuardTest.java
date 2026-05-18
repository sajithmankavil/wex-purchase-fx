package com.example.purchaseconversion.api.advice;

import com.example.purchaseconversion.api.advice.exception.PanPatternDetectedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ContentGuard} — covers all four AC-010 layers:
 * AC-010b (PAN-Luhn), AC-010c (track 1/2), AC-010d (encoded-PAN), AC-010e (NFKC).
 */
class ContentGuardTest {

    private final ContentGuard guard = new ContentGuard();

    /** Visa test PAN — Luhn-valid (passes the checksum). */
    private static final String VALID_PAN = "4242424242424242";
    /** Luhn-invalid 16-digit sequence (last digit altered). */
    private static final String INVALID_PAN = "4242424242424241";

    @Nested
    @DisplayName("Null / blank / clean inputs (no rejection)")
    class Cleanly {

        @Test
        @DisplayName("null input is a no-op")
        void nullInput() {
            assertThatCode(() -> guard.check(null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("empty input is a no-op")
        void emptyInput() {
            assertThatCode(() -> guard.check("")).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Coffee",
                "Lunch at café — 3 people",
                "Phone bill 555-1234",      // 7 digits — below the 13-digit PAN window
                "ID-99999"                  // 5 digits, below window
        })
        @DisplayName("benign strings pass")
        void benign(String s) {
            assertThatCode(() -> guard.check(s)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("12-digit purely-numeric string passes — below PAN window")
        void twelveDigits() {
            assertThatCode(() -> guard.check("123456789012")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("16-digit Luhn-invalid sequence passes (no rejection on invalid PANs)")
        void luhnInvalid() {
            assertThatCode(() -> guard.check(INVALID_PAN)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("AC-010b — PAN-Luhn rejection")
    class PanLuhn {

        @Test
        @DisplayName("Luhn-valid 16-digit PAN → reject (reason=luhn)")
        void plainPan() {
            assertThatThrownBy(() -> guard.check("PAN: " + VALID_PAN))
                    .isInstanceOf(PanPatternDetectedException.class)
                    .satisfies(t -> assertThat(((PanPatternDetectedException) t).getReason()).isEqualTo("luhn"));
        }

        @Test
        @DisplayName("Luhn-valid PAN with spaces is rejected (whitespace stripped)")
        void panWithSpaces() {
            assertThatThrownBy(() -> guard.check("4242 4242 4242 4242"))
                    .isInstanceOf(PanPatternDetectedException.class);
        }

        @Test
        @DisplayName("Luhn-valid PAN with dashes is rejected")
        void panWithDashes() {
            assertThatThrownBy(() -> guard.check("4242-4242-4242-4242"))
                    .isInstanceOf(PanPatternDetectedException.class);
        }
    }

    @Nested
    @DisplayName("AC-010e — NFKC pre-pass (G8-P0-3)")
    class Nfkc {

        @Test
        @DisplayName("fullwidth-digit PAN normalises to ASCII, then Luhn fires")
        void fullwidthPan() {
            String fullwidth = "４２４２ ４２４２ ４２４２ ４２４２";
            assertThat(guard.normaliseForTest(fullwidth))
                    .as("NFKC must rewrite fullwidth digits to ASCII")
                    .isEqualTo("4242 4242 4242 4242");
            assertThatThrownBy(() -> guard.check(fullwidth))
                    .isInstanceOf(PanPatternDetectedException.class)
                    .satisfies(t -> assertThat(((PanPatternDetectedException) t).getReason()).isEqualTo("luhn"));
        }
    }

    @Nested
    @DisplayName("AC-010d — encoded-PAN rejection (G4-P0-5)")
    class Encoded {

        @Test
        @DisplayName("base64-standard PAN rejected (reason=luhn-encoded)")
        void base64Standard() {
            String encoded = Base64.getEncoder().encodeToString(VALID_PAN.getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> guard.check(encoded))
                    .isInstanceOf(PanPatternDetectedException.class)
                    .satisfies(t -> assertThat(((PanPatternDetectedException) t).getReason()).isEqualTo("luhn-encoded"));
        }

        @Test
        @DisplayName("base64-urlsafe PAN rejected")
        void base64UrlSafe() {
            String encoded = Base64.getUrlEncoder().encodeToString(VALID_PAN.getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> guard.check(encoded))
                    .isInstanceOf(PanPatternDetectedException.class);
        }

        @Test
        @DisplayName("hex-encoded PAN rejected")
        void hexEncoded() {
            String encoded = HexFormat.of().formatHex(VALID_PAN.getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> guard.check(encoded))
                    .isInstanceOf(PanPatternDetectedException.class);
        }

        @Test
        @DisplayName("URL-encoded PAN rejected")
        void urlEncoded() {
            String encoded = "PAN%3A%20" + VALID_PAN;
            assertThatThrownBy(() -> guard.check(encoded))
                    .isInstanceOf(PanPatternDetectedException.class);
        }
    }

    @Nested
    @DisplayName("AC-010c — track-data rejection")
    class TrackData {

        @Test
        @DisplayName("Track-1 shape rejected")
        void track1() {
            String track1 = "%B" + VALID_PAN + "^DOE/JOHN^25081011000000000";
            assertThatThrownBy(() -> guard.check(track1))
                    .isInstanceOf(PanPatternDetectedException.class)
                    .satisfies(t -> {
                        String r = ((PanPatternDetectedException) t).getReason();
                        assertThat(r).isIn("luhn", "track1");
                    });
        }

        @Test
        @DisplayName("Track-2 shape rejected")
        void track2() {
            String track2 = ";" + VALID_PAN + "=25081011000000000";
            assertThatThrownBy(() -> guard.check(track2))
                    .isInstanceOf(PanPatternDetectedException.class)
                    .satisfies(t -> {
                        String r = ((PanPatternDetectedException) t).getReason();
                        assertThat(r).isIn("luhn", "track2");
                    });
        }
    }
}
