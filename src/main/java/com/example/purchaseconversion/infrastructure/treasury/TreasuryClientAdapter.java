package com.example.purchaseconversion.infrastructure.treasury;

import com.example.purchaseconversion.application.exception.UpstreamBadResponseException;
import com.example.purchaseconversion.application.exception.UpstreamUnavailableException;
import com.example.purchaseconversion.application.port.out.ExchangeRateRepositoryPort;
import com.example.purchaseconversion.application.port.out.TreasuryClientPort;
import com.example.purchaseconversion.domain.CurrencyDescriptor;
import com.example.purchaseconversion.domain.ExchangeRate;
import com.example.purchaseconversion.observability.MetricsCatalog;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Live implementation of {@link TreasuryClientPort} (ADR-0001 D-9; AC-T-3; AC-027b/c/d/e).
 *
 * <h2>Behaviour</h2>
 * <ul>
 *   <li><b>HTTP:</b> Spring {@link RestClient} against the Fiscal Data API. User-Agent
 *       set per Treasury best practice (G-P2-5). Inbound JSON is mapped to
 *       {@link TreasuryResponse} and shape-validated programmatically (every row
 *       must carry {@code country_currency_desc}, {@code record_date},
 *       {@code effective_date}, {@code exchange_rate}; missing or unparseable
 *       fields raise {@link UpstreamBadResponseException}).</li>
 *   <li><b>Sanity bounds:</b> per row, {@code 0 < exchange_rate ≤ 1e30}; out-of-bound
 *       rows raise {@link UpstreamBadResponseException} per AC-024 / AC-024b.</li>
 *   <li><b>Scale-6 normalisation:</b> every persisted rate is {@code BigDecimal} at
 *       scale 6 per ADR-0001 D-4 / D-10 / G4-P0-3.</li>
 *   <li><b>Resilience4j:</b> programmatic decorators (Bulkhead → Retry → CircuitBreaker)
 *       wrap the actual HTTP call. Annotations would not work here because the gate-
 *       wrapped HTTP call lives behind a self-invocation; programmatic decorators on
 *       the supplier passed to {@link SingleFlightGate#runOnce} avoid that pitfall.</li>
 *   <li><b>Single-flight:</b> Wrapped via {@link SingleFlightGate}, keyed by
 *       {@code (currency, treasury_quarter_end)} (G4-P0-1). Losers poll the DB via
 *       {@link ExchangeRateRepositoryPort#findInWindow}.</li>
 * </ul>
 *
 * <p>Returns an empty list if Treasury responded successfully with no rows in the
 * window (AC-020b / AC-022b) — the caller (ConversionService) maps that to
 * {@code ConversionRateNotAvailableException}.
 */
@Component
public class TreasuryClientAdapter implements TreasuryClientPort {

    private static final Logger LOG = LoggerFactory.getLogger(TreasuryClientAdapter.class);

    /** Sanity ceiling per Phase-6 G6-P1-3. */
    private static final BigDecimal MAX_RATE = new BigDecimal("1e30");

    /** Scale-6 normalisation per ADR-0001 D-4 / D-10. */
    private static final int PERSISTED_SCALE = 6;

    /** Resilience4j registries advertise the named instances configured in application.yml. */
    private static final String INSTANCE_NAME = "treasuryClient";

    /**
     * Defense-in-depth at the Treasury filter trust boundary (B2 30-review §3
     * carry-forward — {@code treasury-filter-boundary-whitelist-assertion}).
     * Canonical Treasury descriptors are A-Z / a-z / 0-9 / space / hyphen /
     * parentheses; comma and colon are reserved by the Fiscal Data API filter
     * grammar and would split a single filter expression into multiple if
     * smuggled through.
     */
    private static final java.util.regex.Pattern DESCRIPTOR_WHITELIST =
            java.util.regex.Pattern.compile("^[A-Za-z][A-Za-z0-9 ()\\-]+$");

    private final RestClient restClient;
    private final SingleFlightGate singleFlightGate;
    private final ExchangeRateRepositoryPort exchangeRateRepository;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;
    private final String ratesPath;

    /**
     * C3 §S4 carry-forward — wired by Spring via setter (autowired-required-false) so existing
     * unit tests can construct the adapter without a catalog. Production beans receive the
     * real registry-backed instance.
     */
    private MetricsCatalog metrics;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setMetrics(MetricsCatalog metrics) {
        this.metrics = metrics;
    }

    public TreasuryClientAdapter(
            RestClient.Builder restClientBuilder,
            SingleFlightGate singleFlightGate,
            ExchangeRateRepositoryPort exchangeRateRepository,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            BulkheadRegistry bulkheadRegistry,
            @Value("${wex.treasury.base-url}") String baseUrl,
            @Value("${wex.treasury.rates-path}") String ratesPath,
            @Value("${wex.treasury.user-agent}") String userAgent) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", userAgent)
                .defaultHeader("Accept", "application/json")
                .build();
        this.singleFlightGate = Objects.requireNonNull(singleFlightGate, "singleFlightGate must not be null");
        this.exchangeRateRepository = Objects.requireNonNull(exchangeRateRepository, "exchangeRateRepository must not be null");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCE_NAME);
        this.retry = retryRegistry.retry(INSTANCE_NAME);
        this.bulkhead = bulkheadRegistry.bulkhead(INSTANCE_NAME);
        this.ratesPath = Objects.requireNonNull(ratesPath, "ratesPath must not be null");
    }

    @Override
    public List<ExchangeRate> fetchRates(
            CurrencyDescriptor currency, LocalDate windowLower, LocalDate windowUpper) {
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(windowLower, "windowLower must not be null");
        Objects.requireNonNull(windowUpper, "windowUpper must not be null");

        // Defense-in-depth filter-boundary check (B2 30-review §3).
        if (!DESCRIPTOR_WHITELIST.matcher(currency.value()).matches()) {
            throw new UpstreamBadResponseException(
                    "schema_invalid:currency_descriptor_boundary:" + currency.value());
        }

        SingleFlightGate.Key key = SingleFlightGate.Key.forTransactionDate(currency, windowUpper);
        FetchHolder holder = new FetchHolder();

        SingleFlightGate.Outcome outcome = singleFlightGate.runOnce(
                key,
                () -> {
                    List<ExchangeRate> fetched = resilientFetch(currency, windowLower, windowUpper);
                    holder.fetched = fetched;
                    return fetched;
                },
                () -> !exchangeRateRepository.findInWindow(currency, windowLower, windowUpper).isEmpty());

        if (outcome == SingleFlightGate.Outcome.WINNER_SUCCESS) {
            return holder.fetched == null ? Collections.emptyList() : holder.fetched;
        }
        // Loser hit DB — caller (ConversionService) reads via the repository next.
        return Collections.emptyList();
    }

    /**
     * Wraps the raw HTTP call with Bulkhead → Retry → CircuitBreaker. Returns a
     * parsed-and-validated list of rates; persists them via the repository.
     * Resilience4j failures are mapped to the application's domain exception types.
     *
     * <p>Emits one structured audit-log line per call (B2 30-review §2
     * carry-forward — {@code treasury-client-audit-log-on-emit}). Fields:
     * {@code currency} (non-sensitive), {@code windowLower}/{@code windowUpper},
     * {@code outcome} (one of {@code success / circuit_open / bulkhead_full /
     * http_5xx:&lt;n&gt; / http_4xx:&lt;n&gt; / io:&lt;reason&gt; /
     * schema_invalid:&lt;reason&gt; / rate_sanity:&lt;reason&gt;}), {@code latencyMs},
     * and the MDC's correlation-id hash (populated by {@code CorrelationIdFilter}).
     */
    private List<ExchangeRate> resilientFetch(
            CurrencyDescriptor currency, LocalDate windowLower, LocalDate windowUpper) {
        Supplier<List<ExchangeRate>> chain = () -> doRawFetchAndPersist(currency, windowLower, windowUpper);
        chain = CircuitBreaker.decorateSupplier(circuitBreaker, chain);
        chain = Retry.decorateSupplier(retry, chain);
        chain = Bulkhead.decorateSupplier(bulkhead, chain);
        long started = System.nanoTime();
        try {
            List<ExchangeRate> out = chain.get();
            audit(currency, windowLower, windowUpper, "success", started, null);
            return out;
        } catch (CallNotPermittedException e) {
            audit(currency, windowLower, windowUpper, "circuit_open", started, e);
            throw new UpstreamUnavailableException("circuit_open", e);
        } catch (BulkheadFullException e) {
            audit(currency, windowLower, windowUpper, "bulkhead_full", started, e);
            throw new UpstreamUnavailableException("bulkhead_full", e);
        } catch (UpstreamBadResponseException e) {
            audit(currency, windowLower, windowUpper, e.getReason(), started, e);
            throw e;
        } catch (UpstreamUnavailableException e) {
            audit(currency, windowLower, windowUpper, e.getReason(), started, e);
            throw e;
        } catch (HttpServerErrorException e) {
            String reason = "http_5xx:" + e.getStatusCode().value();
            audit(currency, windowLower, windowUpper, reason, started, e);
            throw new UpstreamUnavailableException(reason, e);
        } catch (HttpClientErrorException e) {
            String reason = "http_4xx:" + e.getStatusCode().value();
            audit(currency, windowLower, windowUpper, reason, started, e);
            throw new UpstreamBadResponseException(reason, e);
        } catch (ResourceAccessException e) {
            String reason = "io:" + e.getMessage();
            audit(currency, windowLower, windowUpper, reason, started, e);
            throw new UpstreamUnavailableException(reason, e);
        }
    }

    /**
     * Closes C 30-review §4.5 — use {@code StructuredArguments.kv(...)} so the
     * logstash-logback-encoder JSON output places {@code currency}, window
     * bounds, {@code outcome}, {@code latencyMs}, and {@code errClass} as
     * TOP-LEVEL JSON fields rather than concatenated into {@code message}. The
     * structured form is what downstream parsers (Loki/Splunk/ELK) ingest
     * directly without regex.
     */
    private void audit(
            CurrencyDescriptor currency, LocalDate windowLower, LocalDate windowUpper,
            String outcome, long startedNanos, Throwable err) {
        long latencyMs = (System.nanoTime() - startedNanos) / 1_000_000L;
        if (err == null) {
            LOG.info("treasury.client.call",
                    net.logstash.logback.argument.StructuredArguments.kv("currency", currency.value()),
                    net.logstash.logback.argument.StructuredArguments.kv("windowLower", windowLower.toString()),
                    net.logstash.logback.argument.StructuredArguments.kv("windowUpper", windowUpper.toString()),
                    net.logstash.logback.argument.StructuredArguments.kv("outcome", outcome),
                    net.logstash.logback.argument.StructuredArguments.kv("latencyMs", latencyMs));
        } else {
            LOG.warn("treasury.client.call",
                    net.logstash.logback.argument.StructuredArguments.kv("currency", currency.value()),
                    net.logstash.logback.argument.StructuredArguments.kv("windowLower", windowLower.toString()),
                    net.logstash.logback.argument.StructuredArguments.kv("windowUpper", windowUpper.toString()),
                    net.logstash.logback.argument.StructuredArguments.kv("outcome", outcome),
                    net.logstash.logback.argument.StructuredArguments.kv("latencyMs", latencyMs),
                    net.logstash.logback.argument.StructuredArguments.kv("errClass", err.getClass().getSimpleName()));
        }
        // C3 §S4 carry-forward — emit treasury.client.requests{outcome=<...>}
        if (metrics != null) {
            metrics.treasuryRequest(outcome);
        }
    }

    /** Raw HTTP fetch + parse + persist; no resilience wrapping, no gate. */
    private List<ExchangeRate> doRawFetchAndPersist(
            CurrencyDescriptor currency, LocalDate windowLower, LocalDate windowUpper) {
        TreasuryResponse response;
        ResponseEntity<TreasuryResponse> entity = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(ratesPath)
                        .queryParam("filter", "country_currency_desc:eq:" + currency.value()
                                + ",record_date:gte:" + windowLower
                                + ",record_date:lte:" + windowUpper)
                        .queryParam("fields", "country_currency_desc,record_date,effective_date,exchange_rate")
                        .build())
                .retrieve()
                .toEntity(TreasuryResponse.class);
        response = entity.getBody();
        if (response == null || response.data() == null) {
            throw new UpstreamBadResponseException("empty_envelope");
        }
        List<ExchangeRate> parsed = new ArrayList<>(response.data().size());
        for (TreasuryResponse.Row row : response.data()) {
            parsed.add(parseAndValidate(row));
        }
        if (!parsed.isEmpty()) {
            exchangeRateRepository.upsertVersioned(parsed);
        }
        return parsed;
    }

    private ExchangeRate parseAndValidate(TreasuryResponse.Row row) {
        if (row == null) {
            throw new UpstreamBadResponseException("schema_invalid:null_row");
        }
        if (row.countryCurrencyDesc() == null || row.countryCurrencyDesc().isBlank()) {
            throw new UpstreamBadResponseException("schema_invalid:missing_country_currency_desc");
        }
        LocalDate recordDate = parseDateOrThrow(row.recordDate(), "record_date");
        LocalDate effectiveDate = parseDateOrThrow(row.effectiveDate(), "effective_date");
        BigDecimal rate = parseRateOrThrow(row.exchangeRate());

        if (rate.signum() <= 0) {
            throw new UpstreamBadResponseException("rate_sanity:non_positive:" + rate);
        }
        if (rate.compareTo(MAX_RATE) > 0) {
            throw new UpstreamBadResponseException("rate_sanity:above_ceiling:" + rate);
        }

        BigDecimal normalised = rate.setScale(PERSISTED_SCALE, RoundingMode.HALF_UP);
        return new ExchangeRate(
                CurrencyDescriptor.of(row.countryCurrencyDesc()),
                recordDate,
                effectiveDate,
                normalised);
    }

    private static LocalDate parseDateOrThrow(String s, String field) {
        if (s == null || s.isBlank()) {
            throw new UpstreamBadResponseException("schema_invalid:missing_" + field);
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new UpstreamBadResponseException("schema_invalid:unparseable_" + field + ":" + s, e);
        }
    }

    private static BigDecimal parseRateOrThrow(String s) {
        if (s == null || s.isBlank()) {
            throw new UpstreamBadResponseException("schema_invalid:missing_exchange_rate");
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new UpstreamBadResponseException("schema_invalid:unparseable_exchange_rate:" + s, e);
        }
    }

    /** Small holder so the winner's fetched list can be captured from inside the gate. */
    private static final class FetchHolder {
        List<ExchangeRate> fetched;
    }
}
