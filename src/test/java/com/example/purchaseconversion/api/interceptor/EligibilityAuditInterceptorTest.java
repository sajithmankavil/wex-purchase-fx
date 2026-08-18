package com.example.purchaseconversion.api.interceptor;

import com.example.purchaseconversion.observability.EligibilityAuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Proves {@link EligibilityAuditInterceptor}'s two contractual properties
 * (eligibility-endpoint-spec.md §3.4): it fires only after the response is
 * complete, and it is best-effort — a failure here can never surface as anything
 * other than a WARN log line.
 */
@ExtendWith(MockitoExtension.class)
class EligibilityAuditInterceptorTest {

    @Mock
    private EligibilityAuditLogger auditLogger;

    private EligibilityAuditInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new EligibilityAuditInterceptor(auditLogger);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("afterCompletion logs the stamped benefitId/tier/eligible plus a non-negative latency")
    void logsExpectedFieldsWithLatency() throws InterruptedException {
        interceptor.preHandle(request, response, new Object());
        stampEligibilityAttributes(true);

        Thread.sleep(5); // ensure a non-zero, measurable elapsed window

        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<Long> latencyCaptor = ArgumentCaptor.forClass(Long.class);
        verify(auditLogger).logCheck(org.mockito.ArgumentMatchers.eq("BEN-1042"),
                org.mockito.ArgumentMatchers.eq("SIGNATURE"),
                org.mockito.ArgumentMatchers.eq(true),
                latencyCaptor.capture());
        assertThat(latencyCaptor.getValue()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("fires from afterCompletion — runs even once the response is already marked committed/sent")
    void firesAfterResponseIsComplete() {
        interceptor.preHandle(request, response, new Object());
        stampEligibilityAttributes(true);
        response.setStatus(200);
        response.setCommitted(true); // simulates the response already having been sent to the client

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(auditLogger).logCheck(org.mockito.ArgumentMatchers.eq("BEN-1042"),
                org.mockito.ArgumentMatchers.eq("SIGNATURE"),
                org.mockito.ArgumentMatchers.eq(true),
                anyLong());
    }

    @Test
    @DisplayName("no audit line when the controller never reached an eligibility determination (error paths)")
    void noLineWhenAttributesAbsent() {
        interceptor.preHandle(request, response, new Object());
        // No eligibility attributes stamped — simulates a 400/404 short-circuit.

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(auditLogger, never()).logCheck(anyString(), anyString(), anyBoolean(), anyLong());
    }

    @Test
    @DisplayName("best-effort: a logger failure is swallowed, never rethrown")
    void loggerFailureIsSwallowed() {
        interceptor.preHandle(request, response, new Object());
        stampEligibilityAttributes(false);
        doThrow(new RuntimeException("logging backend down"))
                .when(auditLogger).logCheck(anyString(), anyString(), anyBoolean(), anyLong());

        assertThatCode(() -> interceptor.afterCompletion(request, response, new Object(), null))
                .doesNotThrowAnyException();
    }

    private void stampEligibilityAttributes(boolean eligible) {
        request.setAttribute(EligibilityAuditInterceptor.ATTR_BENEFIT_ID, "BEN-1042");
        request.setAttribute(EligibilityAuditInterceptor.ATTR_TIER, "SIGNATURE");
        request.setAttribute(EligibilityAuditInterceptor.ATTR_ELIGIBLE, eligible);
    }
}
