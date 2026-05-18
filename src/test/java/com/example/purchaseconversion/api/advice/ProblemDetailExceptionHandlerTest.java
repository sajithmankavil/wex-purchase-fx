package com.example.purchaseconversion.api.advice;

import com.example.purchaseconversion.api.advice.exception.PanPatternDetectedException;
import com.example.purchaseconversion.application.exception.ConversionRateNotAvailableException;
import com.example.purchaseconversion.application.exception.FutureDateException;
import com.example.purchaseconversion.application.exception.InvalidCurrencyException;
import com.example.purchaseconversion.application.exception.MalformedIdentifierException;
import com.example.purchaseconversion.application.exception.PurchaseNotFoundException;
import com.example.purchaseconversion.application.exception.UpstreamBadResponseException;
import com.example.purchaseconversion.application.exception.UpstreamUnavailableException;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.domain.PurchaseId;
import com.example.purchaseconversion.observability.DescriptionHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProblemDetailExceptionHandler} — RFC 9457 envelope
 * shape, status mapping, header surface, and the A2 carry-forward §5
 * (currency-input hashing on {@link InvalidCurrencyException}).
 */
class ProblemDetailExceptionHandlerTest {

    private ProblemDetailExceptionHandler handler;
    private DescriptionHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new DescriptionHasher("test", "", "v1");
        handler = new ProblemDetailExceptionHandler(hasher);
    }

    @Test
    @DisplayName("AC-T-5 — every response carries application/problem+json")
    void contentTypeAlwaysProblemJson() {
        ResponseEntity<ProblemDetail> resp = handler.onFutureDate(new FutureDateException(LocalDate.now().plusDays(1)));
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("FutureDate → 422 FUTURE_DATE; details.transactionDate present")
    void futureDate() {
        LocalDate d = LocalDate.of(2050, 1, 1);
        ResponseEntity<ProblemDetail> r = handler.onFutureDate(new FutureDateException(d));
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        assertThat(propAsString(r, "errorCode")).isEqualTo("FUTURE_DATE");
        assertThat(detailsMap(r)).containsEntry("transactionDate", d.toString());
    }

    @Test
    @DisplayName("PurchaseNotFound → 404 PURCHASE_NOT_FOUND")
    void purchaseNotFound() {
        PurchaseId id = PurchaseId.next();
        ResponseEntity<ProblemDetail> r = handler.onPurchaseNotFound(new PurchaseNotFoundException(id));
        assertThat(r.getStatusCode().value()).isEqualTo(404);
        assertThat(propAsString(r, "errorCode")).isEqualTo("PURCHASE_NOT_FOUND");
    }

    @Test
    @DisplayName("MalformedIdentifier → 400 MALFORMED_IDENTIFIER")
    void malformedId() {
        // C 30-review §4.7 (NEW MED) — input must be hashed in both log AND response body.
        String pan = "4242424242424242";
        ResponseEntity<ProblemDetail> r = handler.onMalformedId(new MalformedIdentifierException(pan));
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(propAsString(r, "errorCode")).isEqualTo("MALFORMED_IDENTIFIER");

        Map<String, Object> details = detailsMap(r);
        assertThat(details).containsEntry("reason", "malformed-uuid");

        @SuppressWarnings("unchecked")
        Map<String, Object> idDetails = (Map<String, Object>) details.get("id");
        assertThat(idDetails).isNotNull();
        assertThat((String) idDetails.get("hash")).startsWith(hasher.keyVersion() + ":");
        assertThat(idDetails.get("length")).isEqualTo(pan.length());

        // Raw value MUST NOT appear anywhere in the response body.
        assertThat(details.values().toString()).doesNotContain(pan);
        assertThat(idDetails.values().toString()).doesNotContain("4242");
    }

    @Test
    @DisplayName("ConversionRateNotAvailable → 422")
    void rateNotAvailable() {
        ResponseEntity<ProblemDetail> r = handler.onRateNotAvailable(new ConversionRateNotAvailableException(
                LocalDate.of(2026, 5, 17),
                CurrencyDescriptor.of("Canada-Dollar"),
                LocalDate.of(2025, 11, 17),
                LocalDate.of(2026, 5, 17)));
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        assertThat(propAsString(r, "errorCode")).isEqualTo("CONVERSION_RATE_NOT_AVAILABLE");
    }

    @Test
    @DisplayName("UpstreamUnavailable → 503 + Retry-After: 300")
    void upstreamUnavailable() {
        ResponseEntity<ProblemDetail> r = handler.onUpstreamUnavailable(
                new UpstreamUnavailableException("circuit_open"));
        assertThat(r.getStatusCode().value()).isEqualTo(503);
        assertThat(r.getHeaders().getFirst("Retry-After")).isEqualTo("300");
        assertThat(propAsString(r, "errorCode")).isEqualTo("UPSTREAM_UNAVAILABLE");
    }

    @Test
    @DisplayName("UpstreamBadResponse → 502")
    void upstreamBadResponse() {
        ResponseEntity<ProblemDetail> r = handler.onUpstreamBadResponse(
                new UpstreamBadResponseException("rate_sanity:above_ceiling:1.1e30"));
        assertThat(r.getStatusCode().value()).isEqualTo(502);
        assertThat(propAsString(r, "errorCode")).isEqualTo("UPSTREAM_BAD_RESPONSE");
    }

    @Test
    @DisplayName("PanPatternDetected → 400 PAN_PATTERN_DETECTED; reason carried, raw payload NOT in response")
    void panDetected() {
        ResponseEntity<ProblemDetail> r = handler.onPan(new PanPatternDetectedException("luhn"));
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(propAsString(r, "errorCode")).isEqualTo("PAN_PATTERN_DETECTED");
        assertThat(detailsMap(r)).containsEntry("reason", "luhn");
    }

    @Test
    @DisplayName("A2 carry-forward §5 — InvalidCurrencyException response carries HASHED currency only")
    void invalidCurrencyHashedOnly() {
        String rawCurrency = "4242 4242 4242 4242";
        ResponseEntity<ProblemDetail> r = handler.onInvalidCurrency(new InvalidCurrencyException(rawCurrency));

        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(propAsString(r, "errorCode")).isEqualTo("INVALID_CURRENCY");

        Map<String, Object> details = detailsMap(r);
        assertThat(details).containsEntry("reason", "unknown-currency");

        @SuppressWarnings("unchecked")
        Map<String, Object> currency = (Map<String, Object>) details.get("currency");
        assertThat(currency).isNotNull();
        assertThat(currency).containsKey("hash");
        assertThat(currency).containsKey("length");
        assertThat((String) currency.get("hash")).startsWith(hasher.keyVersion() + ":");
        assertThat(currency.get("length")).isEqualTo(rawCurrency.length());

        // CRITICAL: the raw value MUST NOT appear in the response details map.
        assertThat(details.values().toString()).doesNotContain(rawCurrency);
        assertThat(currency.values().toString()).doesNotContain(rawCurrency);
        assertThat(currency.values().toString()).doesNotContain("4242");
    }

    @Test
    @DisplayName("A2 carry-forward §5 — fullwidth-confusable currency also returns hashed-only (AC-010e parallel)")
    void invalidCurrencyFullwidthAlsoHashedOnly() {
        String fullwidth = "４２４２";
        ResponseEntity<ProblemDetail> r = handler.onInvalidCurrency(new InvalidCurrencyException(fullwidth));

        @SuppressWarnings("unchecked")
        Map<String, Object> currency = (Map<String, Object>) detailsMap(r).get("currency");
        assertThat((String) currency.get("hash")).startsWith(hasher.keyVersion() + ":");
        assertThat(currency.values().toString()).doesNotContain(fullwidth);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> detailsMap(ResponseEntity<ProblemDetail> r) {
        return (Map<String, Object>) r.getBody().getProperties().get("details");
    }

    private static String propAsString(ResponseEntity<ProblemDetail> r, String key) {
        return String.valueOf(r.getBody().getProperties().get(key));
    }
}
