package com.example.purchaseconversion.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * HMAC-SHA-256 hasher for log-emission redaction (ADR-0001 D-12; NFR-017;
 * AC-032b). Output is prefixed with the key-version label (default {@code v1:})
 * so a future key-rotation can re-hash old log entries unambiguously.
 *
 * <h2>Refuse-to-start in prod/staging</h2>
 *
 * <p>Per ADR-0001 D-12 + NFR-017: when {@code WEX_LOG_HASH_KEY} is absent in
 * production, the application MUST refuse to start. v1 implements this via a
 * profile check at construction: profiles {@code prod} and {@code staging}
 * require the env-var; {@code dev} / {@code test} / {@code local} fall back to
 * a hardcoded dev-only key prefixed {@code v0:} so test fixtures stay stable.
 *
 * <p>Inputs that are {@code null} or blank return the empty string — the
 * caller decides whether to omit the field or log a sentinel.
 */
@Component
public class DescriptionHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String DEV_KEY = "wex-dev-only-do-not-use-in-prod";

    private final String keyVersion;
    private final byte[] key;

    public DescriptionHasher(
            @Value("${spring.profiles.active:dev}") String activeProfiles,
            @Value("${WEX_LOG_HASH_KEY:}") String envKey,
            @Value("${wex.log.hash-key-version:v1}") String configuredVersion) {
        boolean prodLike = activeProfiles != null
                && (activeProfiles.contains("prod") || activeProfiles.contains("staging"));
        if (prodLike && (envKey == null || envKey.isBlank())) {
            throw new IllegalStateException(
                    "WEX_LOG_HASH_KEY must be set in profile=" + activeProfiles
                            + " (ADR-0001 D-12; NFR-017)");
        }
        if (envKey != null && !envKey.isBlank()) {
            this.keyVersion = configuredVersion;
            this.key = envKey.getBytes(StandardCharsets.UTF_8);
        } else {
            // dev / test / local — stable v0 key for predictable assertions.
            this.keyVersion = "v0";
            this.key = DEV_KEY.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Returns {@code "vN:<hex>"} or empty string for null/blank input. */
    public String hash(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return keyVersion + ":" + toHex(digest);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA-256 unavailable", e);
        }
    }

    /** Visible for tests — exposes the configured prefix. */
    public String keyVersion() {
        return keyVersion;
    }

    private static String toHex(byte[] bytes) {
        Objects.requireNonNull(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
