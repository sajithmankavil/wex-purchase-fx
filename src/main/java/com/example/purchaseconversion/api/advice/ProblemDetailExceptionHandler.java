package com.example.purchaseconversion.api.advice;

import com.example.purchaseconversion.api.advice.exception.PanPatternDetectedException;
import com.example.purchaseconversion.application.exception.BenefitNotFoundException;
import com.example.purchaseconversion.application.exception.ConversionRateNotAvailableException;
import com.example.purchaseconversion.application.exception.DomainException;
import com.example.purchaseconversion.application.exception.FutureDateException;
import com.example.purchaseconversion.application.exception.InvalidCurrencyException;
import com.example.purchaseconversion.application.exception.InvalidTierException;
import com.example.purchaseconversion.application.exception.MalformedIdentifierException;
import com.example.purchaseconversion.application.exception.PurchaseNotFoundException;
import com.example.purchaseconversion.application.exception.UpstreamBadResponseException;
import com.example.purchaseconversion.application.exception.UpstreamUnavailableException;
import com.example.purchaseconversion.observability.DescriptionHasher;
import com.example.purchaseconversion.observability.MetricsCatalog;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralised RFC 9457 {@code application/problem+json} handler
 * (component-design.md §4; AC-T-5; ADR-0001 D-11).
 *
 * <p>Every concrete subclass of {@link DomainException} maps to a stable
 * {@code (httpStatus, errorCode, type, title, details)} tuple. Bean Validation
 * + Jackson binding errors are caught here too so the client receives a
 * uniformly-shaped envelope for every error path (AC-T-4).
 *
 * <h2>A2 carry-forward — currency-input hashing on emit (15-clarification.md)</h2>
 *
 * <p>{@link InvalidCurrencyException} carries the raw currency input. The
 * handler:
 * <ul>
 *   <li>Logs the value only after passing it through {@link DescriptionHasher}.</li>
 *   <li>Returns the hashed form in the {@code application/problem+json} body's
 *       {@code details.currency.hash} field (the {@code details.currency.raw}
 *       field is omitted). The original length is exposed as
 *       {@code details.currency.length} for client diagnostics.</li>
 * </ul>
 *
 * <p>This closes the A2 review's §5 invariant and protects the audit destination
 * (which is categorised connected-to per pci-scope-and-cde.md §2) from
 * attacker-controlled CHD-shaped strings being smuggled into logs via malformed
 * currency parameters.
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);
    private static final URI BASE = URI.create("https://wex.example.com/problems/");

    private final DescriptionHasher hasher;
    private final MetricsCatalog metrics;

    @Autowired
    public ProblemDetailExceptionHandler(DescriptionHasher hasher, @Nullable MetricsCatalog metrics) {
        // MetricsCatalog is optional — @WebMvcTest slices do not load the observability
        // package, so MetricsCatalog won't be in the context. Production beans inject
        // the real instance; @Nullable lets Spring resolve to null when absent.
        this.hasher = hasher;
        this.metrics = metrics;
    }

    /**
     * Test-only constructor (no metrics wiring).
     * Not annotated {@code @Autowired} so Spring chooses the 2-arg constructor at runtime.
     */
    public ProblemDetailExceptionHandler(DescriptionHasher hasher) {
        this(hasher, null);
    }

    // --- Validation paths ---

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> onBeanValidation(MethodArgumentNotValidException e) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "code", String.valueOf(fe.getCode()),
                        "message", fe.getDefaultMessage() == null
                                ? DefaultMessageSourceResolvable.class.getSimpleName()
                                : fe.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Bean validation failed", Map.of("errors", errors), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> onUnreadableBody(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request body could not be parsed", Map.of("reason", "unreadable-body"), null);
    }

    /**
     * Shared across every controller with a required {@code @RequestParam}. The
     * eligibility endpoint's missing-{@code tier} case (spec §2) gets the specific
     * {@code MISSING_TIER} errorCode it documents; any other missing-required-param
     * case (e.g. the purchases endpoint's {@code currency}) gets the generic
     * {@code MISSING_PARAMETER} rather than a misleading tier-specific code.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> onMissingParameter(MissingServletRequestParameterException e) {
        String errorCode = "tier".equals(e.getParameterName()) ? "MISSING_TIER" : "MISSING_PARAMETER";
        return respond(HttpStatus.BAD_REQUEST, errorCode,
                "Required parameter is missing",
                Map.of("parameter", e.getParameterName()), null);
    }

    // --- Content guard ---

    @ExceptionHandler(PanPatternDetectedException.class)
    public ResponseEntity<ProblemDetail> onPan(PanPatternDetectedException e) {
        // The rejected payload is NEVER logged in plain text — only the reason label.
        LOG.warn("purchase_validation_failed reason={}", e.getReason());
        return respond(HttpStatus.BAD_REQUEST, "PAN_PATTERN_DETECTED",
                "PAN-shaped content detected",
                Map.of("reason", e.getReason()), null);
    }

    // --- Application domain errors ---

    @ExceptionHandler(FutureDateException.class)
    public ResponseEntity<ProblemDetail> onFutureDate(FutureDateException e) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "FUTURE_DATE",
                "transactionDate is in the future",
                Map.of("transactionDate", String.valueOf(e.getTransactionDate())), null);
    }

    @ExceptionHandler(PurchaseNotFoundException.class)
    public ResponseEntity<ProblemDetail> onPurchaseNotFound(PurchaseNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "PURCHASE_NOT_FOUND",
                "Purchase not found",
                Map.of("id", String.valueOf(e.getId())), null);
    }

    /**
     * Closes C 30-review §4.7 (NEW MED) — {@link MalformedIdentifierException#getInput()}
     * is arbitrary attacker-controlled text (the failure mode is "input was not
     * UUID-shaped" — Luhn-valid PAN-shaped strings can land here). It must NEVER
     * be logged in plain text or echoed in the response body. Mirrors the A2 §5
     * treatment of {@link InvalidCurrencyException}.
     */
    @ExceptionHandler(MalformedIdentifierException.class)
    public ResponseEntity<ProblemDetail> onMalformedId(MalformedIdentifierException e) {
        String hashed = hasher.hash(e.getInput());
        int length = e.getInput() == null ? 0 : e.getInput().length();
        // C3 §S4 carry-forward — StructuredArguments.kv migration (matches the
        // currency_alias.drift emission and TreasuryClientAdapter::audit).
        LOG.warn("malformed_identifier.detected",
                StructuredArguments.kv("idHash", hashed),
                StructuredArguments.kv("idLength", length));
        Map<String, Object> idDetails = new LinkedHashMap<>();
        idDetails.put("hash", hashed);
        idDetails.put("length", length);
        ResponseEntity<ProblemDetail> resp = respond(HttpStatus.BAD_REQUEST, "MALFORMED_IDENTIFIER",
                "Purchase identifier is not a valid UUID v7",
                Map.of("reason", "malformed-uuid", "id", idDetails), null);
        // C 30-review §4.7 follow-up — the RFC 9457 `instance` field defaults to the
        // request URI (set by Spring's ProblemDetail processing), which echoes the
        // raw malformed input in the path (e.g., /api/v1/purchases/<pan>). Override
        // with a redacted placeholder so the raw value never appears in the response.
        ProblemDetail body = resp.getBody();
        if (body != null) {
            body.setInstance(URI.create("/api/v1/purchases/redacted"));
        }
        return resp;
    }

    /**
     * Closes A2 review §5 — the carried currency string MUST NOT be logged in
     * plain text and MUST NOT be echoed in the response body.
     */
    @ExceptionHandler(InvalidCurrencyException.class)
    public ResponseEntity<ProblemDetail> onInvalidCurrency(InvalidCurrencyException e) {
        String hashed = hasher.hash(e.getCurrency());
        int length = e.getCurrency() == null ? 0 : e.getCurrency().length();
        // C3 §S4 carry-forward — StructuredArguments.kv so top-level JSON fields land in the
        // logstash encoder output (Loki/Splunk/ELK consume them directly without regex).
        LOG.warn("currency_alias.drift.detected",
                StructuredArguments.kv("currencyHash", hashed),
                StructuredArguments.kv("currencyLength", length));
        if (metrics != null) {
            metrics.aliasDriftDetected();
        }
        Map<String, Object> currencyDetails = new LinkedHashMap<>();
        currencyDetails.put("hash", hashed);
        currencyDetails.put("length", length);
        return respond(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY",
                "Currency could not be resolved",
                Map.of("reason", "unknown-currency", "currency", currencyDetails), null);
    }

    @ExceptionHandler(ConversionRateNotAvailableException.class)
    public ResponseEntity<ProblemDetail> onRateNotAvailable(ConversionRateNotAvailableException e) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("purchaseDate", String.valueOf(e.getPurchaseDate()));
        details.put("targetCurrency", e.getTargetCurrency().value());
        details.put("windowLower", String.valueOf(e.getWindowLower()));
        details.put("windowUpper", String.valueOf(e.getWindowUpper()));
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "CONVERSION_RATE_NOT_AVAILABLE",
                "No eligible rate in 6-month window",
                details, null);
    }

    @ExceptionHandler(UpstreamUnavailableException.class)
    public ResponseEntity<ProblemDetail> onUpstreamUnavailable(UpstreamUnavailableException e) {
        HttpHeaders extra = new HttpHeaders();
        extra.add(HttpHeaders.RETRY_AFTER, "300");
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE",
                "Upstream Treasury unavailable",
                Map.of("reason", e.getReason()), extra);
    }

    @ExceptionHandler(UpstreamBadResponseException.class)
    public ResponseEntity<ProblemDetail> onUpstreamBadResponse(UpstreamBadResponseException e) {
        return respond(HttpStatus.BAD_GATEWAY, "UPSTREAM_BAD_RESPONSE",
                "Upstream Treasury returned a non-conformant payload",
                Map.of("reason", e.getReason()), null);
    }

    /** eligibility-endpoint-spec.md §3.3 — distinct from "not eligible"; see class-level javadoc there. */
    @ExceptionHandler(BenefitNotFoundException.class)
    public ResponseEntity<ProblemDetail> onBenefitNotFound(BenefitNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "BENEFIT_NOT_FOUND",
                "Benefit not found",
                Map.of("benefitId", e.getBenefitId().value()), null);
    }

    /**
     * eligibility-endpoint-spec.md §3.3 — {@code tier} is a short, bounded-enum-shaped
     * field, not free text; echoed directly (no hashing needed, unlike currency/id).
     */
    @ExceptionHandler(InvalidTierException.class)
    public ResponseEntity<ProblemDetail> onInvalidTier(InvalidTierException e) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_TIER",
                "tier is not a recognized card tier",
                Map.of("tier", e.getTier()), null);
    }

    // --- Catch-all ---

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> onDomain(DomainException e) {
        LOG.warn("domain_exception_unhandled class={}", e.getClass().getName());
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Internal error", Map.of(), null);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ProblemDetail> onUnhandled(Throwable t) {
        // Never leak internal exception details to the client; log the class + correlation MDC only.
        LOG.error("unhandled_exception class={}", t.getClass().getName(), t);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Internal error", Map.of(), null);
    }

    // --- Helpers ---

    private ResponseEntity<ProblemDetail> respond(
            HttpStatus status, String errorCode, String title,
            Map<String, ?> details, HttpHeaders extraHeaders) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, title);
        body.setType(BASE.resolve(errorCode.toLowerCase().replace('_', '-')));
        body.setTitle(title);
        body.setProperty("errorCode", errorCode);
        body.setProperty("details", details);

        HttpHeaders headers = new HttpHeaders();
        if (extraHeaders != null) {
            headers.addAll(extraHeaders);
        }
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(body, headers, status);
    }
}
