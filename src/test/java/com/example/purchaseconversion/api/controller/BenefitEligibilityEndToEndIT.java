package com.example.purchaseconversion.api.controller;

import com.example.purchaseconversion.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * eligibility-endpoint-spec.md §7 — full HTTP path against a real Postgres, plus
 * the behaviours that matter most for this feature's NFR and observability story:
 * <ul>
 *   <li>a DB change is reflected only after the next refresh cycle, not before
 *       (proves the in-memory model isn't accidentally per-request DB-backed —
 *       spec §4.1's whole reason for existing);</li>
 *   <li>the readiness health group genuinely includes {@code benefitEligibility},
 *       not just "the aggregate status happens to be UP" (Spring Boot's implicit
 *       readiness group does NOT auto-include custom HealthIndicator beans —
 *       verified manually via a live local boot; see application.yml's
 *       management.endpoint.health.group.readiness.include);</li>
 *   <li>the global {@code X-Correlation-Id} filter applies to this endpoint (spec
 *       §7's correlation-ID requirement) — not re-tested at the WebMvc-slice level
 *       since that slice disables filters entirely (addFilters=false), so this IT
 *       is the only place it's actually exercised for this specific path.</li>
 * </ul>
 *
 * <p>Cold-start-with-DB-down and refresh-failure-keeps-last-snapshot are proven at
 * the unit level ({@code BenefitEligibilityCacheAdapterTest}) rather than here —
 * simulating a genuinely unreachable DB at Testcontainers boot isn't practical, and
 * the unit tests already isolate exactly that failure path against a mocked
 * repository.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BenefitEligibilityEndToEndIT extends AbstractPostgresIT {

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcClient jdbcClient;

    @DynamicPropertySource
    static void testOverrides(DynamicPropertyRegistry registry) {
        // Short enough to observe a refresh within the test's lifetime without
        // making the suite slow.
        registry.add("wex.eligibility.refresh-interval-ms", () -> "1500");
        // Production default is "when-authorized" (no auth is configured in this
        // drill, so unauthenticated TestRestTemplate calls would never see the
        // component breakdown). Scoped to this test only — does not change
        // production behavior.
        registry.add("management.endpoint.health.show-details", () -> "always");
    }

    @Test
    @DisplayName("happy path — seeded benefit, eligible tier -> 200 eligible:true")
    void happyPathEligible() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/benefits/BEN-1001/eligibility?tier=PLATINUM", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"eligible\":true");
    }

    @Test
    @DisplayName("happy path — seeded benefit, tier below minimum -> 200 eligible:false")
    void happyPathNotEligible() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/benefits/BEN-1003/eligibility?tier=PLATINUM", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"eligible\":false");
    }

    @Test
    @DisplayName("unknown benefit -> 404 BENEFIT_NOT_FOUND, raw id hashed not echoed (spec §2 review correction)")
    void unknownBenefit() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/benefits/BEN-DOES-NOT-EXIST/eligibility?tier=PLATINUM", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).contains("BENEFIT_NOT_FOUND");
        assertThat(response.getBody()).doesNotContain("BEN-DOES-NOT-EXIST");
    }

    @Test
    @DisplayName("a benefit inserted directly into the DB is NOT visible until the next refresh cycle")
    void newRowNotVisibleUntilNextRefresh() throws InterruptedException {
        String benefitId = "BEN-LIVE-" + Instant.now().toEpochMilli();
        jdbcClient.sql("""
                        INSERT INTO benefit_eligibility (benefit_id, minimum_tier, updated_at)
                        VALUES (:id, 'SIGNATURE', now())
                        """)
                .param("id", benefitId)
                .update();

        // Immediately after the DB write, the in-memory cache hasn't refreshed yet —
        // this is the whole point of spec §4.1: no per-request DB read.
        ResponseEntity<String> before = rest.getForEntity(
                "/api/v1/benefits/" + benefitId + "/eligibility?tier=SIGNATURE", String.class);
        assertThat(before.getStatusCode().value()).isEqualTo(404);

        // After the next scheduled refresh cycle, it becomes visible. Poll rather than
        // sleep-then-assert-once, so the test isn't tied to exact scheduler timing.
        ResponseEntity<String> after = pollUntilVisible(benefitId);
        assertThat(after.getStatusCode().value()).isEqualTo(200);
        assertThat(after.getBody()).contains("\"eligible\":true");
    }

    private ResponseEntity<String> pollUntilVisible(String benefitId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        ResponseEntity<String> last;
        do {
            Thread.sleep(200);
            last = rest.getForEntity(
                    "/api/v1/benefits/" + benefitId + "/eligibility?tier=SIGNATURE", String.class);
        } while (last.getStatusCode().value() != 200 && System.currentTimeMillis() < deadline);
        return last;
    }

    @Test
    @DisplayName("readiness group genuinely includes benefitEligibility, UP once the cache has loaded — "
            + "not just \"aggregate status happens to be UP\" (that would pass even for a no-op indicator)")
    void readinessGroupIncludesBenefitEligibility() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health/readiness", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"benefitEligibility\":{\"status\":\"UP\"}");
    }

    @Test
    @DisplayName("supplied X-Correlation-Id is echoed back on this endpoint (spec §7)")
    void correlationIdIsEchoedBack() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Correlation-Id", "test-corr-123");
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/benefits/BEN-1001/eligibility?tier=PLATINUM",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).endsWith("-test-corr-123");
    }

    @Test
    @DisplayName("absent X-Correlation-Id results in one being generated on this endpoint (spec §7)")
    void correlationIdIsGeneratedWhenAbsent() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/benefits/BEN-1001/eligibility?tier=PLATINUM", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
    }
}
