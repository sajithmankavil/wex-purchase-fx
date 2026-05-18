package com.example.purchaseconversion.api.advice;

import com.example.purchaseconversion.api.advice.exception.PanPatternDetectedException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * PAN-Luhn and track-data detection on the {@code description} field
 * (ADR-0001 D-13; AC-010b / AC-010c / AC-010d / AC-010e;
 * G4-P0-5 + G8-P0-3).
 *
 * <h2>Three-step pipeline</h2>
 *
 * <ol>
 *   <li><b>NFKC pre-pass</b> — Unicode normalisation per G8-P0-3, catching
 *       fullwidth-digit PANs (AC-010e — e.g. {@code "４２４２ ４２４２ ４２４２ ４２４２"}).
 *       The STORED {@code description} remains the original (un-normalised)
 *       input; NFKC is applied only inside this guard.</li>
 *   <li><b>Decoder candidate set</b> — base64 (standard + URL-safe), hex, and
 *       URL-encoded variants are decoded if syntactically valid (G4-P0-5 /
 *       AC-010d). Each successful decode adds a candidate string to scan.</li>
 *   <li><b>Apply guards to each candidate</b> — Luhn check on 13–19 digit
 *       sequences (AC-010b) and track-data regex (AC-010c). Any hit → reject.</li>
 * </ol>
 *
 * <p>This class is a {@code @Component} (not a {@code @RestControllerAdvice})
 * so it can be injected into controllers, filters, AND advice. The advice
 * itself ({@code ContentGuardAdvice}) drives the guard pre-controller via
 * Spring MVC's {@code @ModelAttribute} hook on request bindings.
 */
@Component
public class ContentGuard {

    /** 13–19 digit window — credit-card length range across major brands. */
    private static final Pattern PAN_CANDIDATE = Pattern.compile("[0-9]{13,19}");

    /** ISO/IEC 7813 Track 1 magnetic-stripe encoding. */
    private static final Pattern TRACK_1 = Pattern.compile("%B[0-9]{13,19}\\^[^?]{2,26}\\^[0-9]{4,}");

    /** ISO/IEC 7813 Track 2 magnetic-stripe encoding. */
    private static final Pattern TRACK_2 = Pattern.compile(";[0-9]{13,19}=[0-9]{4,}");

    /**
     * Checks {@code input} for PAN / track-data signatures across the NFKC-
     * normalised form and its successful base64/hex/URL-encoded decoded
     * variants. Throws {@link PanPatternDetectedException} on any hit.
     */
    public void check(String input) {
        if (input == null || input.isBlank()) {
            return;
        }
        String normalised = Normalizer.normalize(input, Normalizer.Form.NFKC);
        List<Candidate> candidates = new ArrayList<>(4);
        candidates.add(new Candidate(normalised, /* encoded= */ false));
        addIfDecoded(candidates, tryBase64Standard(normalised), true);
        addIfDecoded(candidates, tryBase64UrlSafe(normalised), true);
        addIfDecoded(candidates, tryHex(normalised), true);
        addIfDecoded(candidates, tryUrlDecoded(normalised), true);

        for (Candidate c : candidates) {
            if (containsLuhnPan(c.text())) {
                throw new PanPatternDetectedException(c.encoded() ? "luhn-encoded" : "luhn");
            }
            if (TRACK_1.matcher(c.text()).find()) {
                throw new PanPatternDetectedException("track1");
            }
            if (TRACK_2.matcher(c.text()).find()) {
                throw new PanPatternDetectedException("track2");
            }
        }
    }

    /** Visible for testing the NFKC pre-pass output. */
    String normaliseForTest(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFKC);
    }

    private static void addIfDecoded(List<Candidate> bucket, String decoded, boolean encoded) {
        if (decoded != null && !decoded.isBlank()) {
            bucket.add(new Candidate(decoded, encoded));
        }
    }

    private static String tryBase64Standard(String s) {
        // Base64 alphabet: A-Z a-z 0-9 + / =  ; need length divisible by 4 and ≥ 16 (covers 13-digit PAN encodings).
        if (s.length() < 16 || !s.matches("^[A-Za-z0-9+/=]+$") || s.length() % 4 != 0) {
            return null;
        }
        try {
            return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String tryBase64UrlSafe(String s) {
        if (s.length() < 16 || !s.matches("^[A-Za-z0-9_\\-=]+$")) {
            return null;
        }
        try {
            return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String tryHex(String s) {
        if (s.length() < 26 || (s.length() % 2) != 0 || !s.matches("^[0-9A-Fa-f]+$")) {
            return null;
        }
        try {
            byte[] bytes = new byte[s.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String tryUrlDecoded(String s) {
        if (s.indexOf('%') < 0 && s.indexOf('+') < 0) {
            return null;
        }
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Returns true if any digit substring of length 13-19 passes the Luhn check. */
    private static boolean containsLuhnPan(String text) {
        // First collapse whitespace + dashes around digit groups (common PAN formatting).
        String collapsed = text.replaceAll("[\\s\\-]", "");
        var matcher = PAN_CANDIDATE.matcher(collapsed);
        while (matcher.find()) {
            if (luhn(matcher.group())) {
                return true;
            }
        }
        return false;
    }

    /** Standard Luhn checksum. */
    private static boolean luhn(String digits) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleDigit) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private record Candidate(String text, boolean encoded) {
        Candidate {
            Objects.requireNonNull(text, "text must not be null");
        }
    }
}
