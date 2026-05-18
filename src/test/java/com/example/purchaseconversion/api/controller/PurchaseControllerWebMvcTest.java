package com.example.purchaseconversion.api.controller;

import com.example.purchaseconversion.api.advice.ContentGuard;
import com.example.purchaseconversion.api.advice.ContentGuardAdvice;
import com.example.purchaseconversion.api.advice.ProblemDetailExceptionHandler;
import com.example.purchaseconversion.api.dto.PurchaseRequest;
import com.example.purchaseconversion.application.conversion.ConversionResult;
import com.example.purchaseconversion.application.exception.FutureDateException;
import com.example.purchaseconversion.application.exception.InvalidCurrencyException;
import com.example.purchaseconversion.application.exception.PurchaseNotFoundException;
import com.example.purchaseconversion.application.port.in.ConvertPurchaseUseCase;
import com.example.purchaseconversion.application.port.in.RegisterPurchaseCommand;
import com.example.purchaseconversion.application.port.in.RegisterPurchaseUseCase;
import com.example.purchaseconversion.application.port.in.RetrievePurchaseUseCase;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.domain.ExchangeRate;
import com.example.purchaseconversion.domain.Money;
import com.example.purchaseconversion.domain.Purchase;
import com.example.purchaseconversion.domain.PurchaseId;
import com.example.purchaseconversion.observability.DescriptionHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvc slice tests for {@link PurchaseController}. Verifies happy paths and
 * RFC 9457 error envelopes across all 9 mapped exception types + Bean Validation.
 *
 * <p>Imports {@link ContentGuard}, {@link ContentGuardAdvice}, and
 * {@link ProblemDetailExceptionHandler} so the request pipeline matches the
 * production wiring (sans the rate-limit filter, which is exercised by
 * RateLimitOrderingIT in the integration test path).
 */
// AutoConfigureMockMvc(addFilters=false) disables Spring's filter chain in the
// slice (the addFilters attribute lives there, not on @WebMvcTest). The
// component-scanned WexRateLimiterFilter still gets instantiated by the slice,
// so a @MockBean for RateLimiterRegistry is provided below. The filter's
// behaviour is covered by WexRateLimiterFilterTest separately; this slice is
// for controller + advice tests.
@WebMvcTest(PurchaseController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ContentGuard.class, ContentGuardAdvice.class, ProblemDetailExceptionHandler.class,
        PurchaseControllerWebMvcTest.HasherConfig.class})
class PurchaseControllerWebMvcTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RegisterPurchaseUseCase registerPurchase;
    @MockBean private RetrievePurchaseUseCase retrievePurchase;
    @MockBean private ConvertPurchaseUseCase convertPurchase;
    // Satisfies WexRateLimiterFilter's constructor (the filter is component-scanned
    // by the slice but isn't applied — addFilters=false above disables the chain).
    @MockBean private RateLimiterRegistry rateLimiterRegistry;

    private static final String VALID_V7_ID = newV7Id();

    @Test
    @DisplayName("POST /api/v1/purchases — happy path returns 201 with Location header")
    void createHappy() throws Exception {
        PurchaseId id = PurchaseId.fromString(VALID_V7_ID);
        Purchase saved = new Purchase(id, "Coffee", LocalDate.of(2026, 5, 10), Money.of("4.50"));
        when(registerPurchase.register(any(RegisterPurchaseCommand.class))).thenReturn(saved);

        mvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PurchaseRequest(
                                "Coffee", LocalDate.of(2026, 5, 10), new BigDecimal("4.50")))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/purchases/" + id)))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.description").value("Coffee"))
                .andExpect(jsonPath("$.amountUsd").value("4.50"));
    }

    @Test
    @DisplayName("POST — Bean Validation: blank description → 400 VALIDATION_FAILED")
    void createBlankDescription() throws Exception {
        mvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"","transactionDate":"2026-05-10","amountUsd":"4.50"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST — Bean Validation: 51-char description → 400")
    void createTooLong() throws Exception {
        String tooLong = "A".repeat(51);
        mvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"%s","transactionDate":"2026-05-10","amountUsd":"4.50"}""".formatted(tooLong)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST — Bean Validation: scale > 2 → 400")
    void createBadScale() throws Exception {
        mvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Coffee","transactionDate":"2026-05-10","amountUsd":"4.500"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST — ContentGuard: PAN-shaped description → 400 PAN_PATTERN_DETECTED")
    void createPanDetected() throws Exception {
        mvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"PAN 4242 4242 4242 4242","transactionDate":"2026-05-10","amountUsd":"4.50"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("PAN_PATTERN_DETECTED"))
                .andExpect(jsonPath("$.details.reason").value("luhn"));
    }

    @Test
    @DisplayName("POST — ContentGuard: fullwidth-digit PAN → 400 (AC-010e)")
    void createFullwidthPan() throws Exception {
        mvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"PAN ４２４２ ４２４２ ４２４２ ４２４２","transactionDate":"2026-05-10","amountUsd":"4.50"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAN_PATTERN_DETECTED"));
    }

    @Test
    @DisplayName("POST — FutureDate from service → 422")
    void createFutureDate() throws Exception {
        when(registerPurchase.register(any(RegisterPurchaseCommand.class)))
                .thenThrow(new FutureDateException(LocalDate.of(2099, 1, 1)));

        mvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Coffee","transactionDate":"2099-01-01","amountUsd":"4.50"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("FUTURE_DATE"));
    }

    @Test
    @DisplayName("GET /{id} — happy path → 200")
    void retrieveHappy() throws Exception {
        PurchaseId id = PurchaseId.fromString(VALID_V7_ID);
        Purchase stored = new Purchase(id, "Coffee", LocalDate.of(2026, 5, 10), Money.of("4.50"));
        when(retrievePurchase.retrieve(any(PurchaseId.class))).thenReturn(stored);

        mvc.perform(get("/api/v1/purchases/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @DisplayName("GET /{id} — malformed UUID → 400 MALFORMED_IDENTIFIER")
    void retrieveMalformed() throws Exception {
        mvc.perform(get("/api/v1/purchases/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_IDENTIFIER"));
    }

    @Test
    @DisplayName("C 30-review §4.7 — Luhn-PAN-shaped id in path is hashed in response; no raw '4242' echoed")
    void retrieveMalformedHashesInput() throws Exception {
        String pan = "4242424242424242";
        mvc.perform(get("/api/v1/purchases/{id}", pan))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_IDENTIFIER"))
                .andExpect(jsonPath("$.details.reason").value("malformed-uuid"))
                .andExpect(jsonPath("$.details.id.hash").value(notNullValue()))
                .andExpect(jsonPath("$.details.id.length").value(equalTo(pan.length())))
                // Critical: raw "4242" substring MUST NOT appear in the response body.
                .andExpect(content().string(not(containsString("4242"))));
    }

    @Test
    @DisplayName("GET /{id} — not found → 404 PURCHASE_NOT_FOUND")
    void retrieveNotFound() throws Exception {
        PurchaseId id = PurchaseId.fromString(VALID_V7_ID);
        when(retrievePurchase.retrieve(any(PurchaseId.class)))
                .thenThrow(new PurchaseNotFoundException(id));
        mvc.perform(get("/api/v1/purchases/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PURCHASE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /{id}/conversion — happy path → 200")
    void convertHappy() throws Exception {
        PurchaseId id = PurchaseId.fromString(VALID_V7_ID);
        Purchase p = new Purchase(id, "Coffee", LocalDate.of(2026, 5, 10), Money.of("123.45"));
        ExchangeRate r = new ExchangeRate(
                CurrencyDescriptor.of("Canada-Dollar"),
                LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 5, 10),
                new BigDecimal("1.370000"));
        when(convertPurchase.convert(any(PurchaseId.class), any(String.class)))
                .thenReturn(new ConversionResult(p, r, Money.of("169.13")));

        mvc.perform(get("/api/v1/purchases/{id}/conversion", id).param("currency", "CAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetCurrency").value("Canada-Dollar"))
                .andExpect(jsonPath("$.exchangeRate").value("1.370000"))
                .andExpect(jsonPath("$.convertedAmount").value("169.13"));
    }

    @Test
    @DisplayName("GET /{id}/conversion — A2 §5 — invalid currency response hashes the input")
    void convertInvalidCurrencyHashesInput() throws Exception {
        PurchaseId id = PurchaseId.fromString(VALID_V7_ID);
        String pan = "4242 4242 4242 4242";
        doThrow(new InvalidCurrencyException(pan))
                .when(convertPurchase).convert(any(PurchaseId.class), any(String.class));

        mvc.perform(get("/api/v1/purchases/{id}/conversion", id).param("currency", pan))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CURRENCY"))
                .andExpect(jsonPath("$.details.reason").value("unknown-currency"))
                .andExpect(jsonPath("$.details.currency.hash").value(notNullValue()))
                .andExpect(jsonPath("$.details.currency.length").value(equalTo(pan.length())))
                // Critical: the raw "4242" digit substring MUST NOT appear anywhere in the response.
                .andExpect(content().string(not(containsString("4242"))));
    }

    private static String newV7Id() {
        return PurchaseId.next().toString();
    }

    @TestConfiguration
    static class HasherConfig {
        @Bean DescriptionHasher hasher() { return new DescriptionHasher("test", "", "v1"); }
    }
}
