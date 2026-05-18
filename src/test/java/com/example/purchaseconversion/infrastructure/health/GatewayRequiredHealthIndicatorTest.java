package com.example.purchaseconversion.infrastructure.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRequiredHealthIndicatorTest {

    @Test
    @DisplayName("WEX_GATEWAY_REQUIRED unset → UP")
    void notRequired() {
        Health h = new GatewayRequiredHealthIndicator(false, "").health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("R-008 — required but trust header unset → DOWN")
    void requiredButNotConfigured() {
        Health h = new GatewayRequiredHealthIndicator(true, "").health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails()).containsEntry("reason", "WEX_GATEWAY_TRUST_HEADER not configured");
    }

    @Test
    @DisplayName("required + trust header configured → UP")
    void requiredAndConfigured() {
        Health h = new GatewayRequiredHealthIndicator(true, "X-Gateway-Trust").health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails()).containsEntry("trustHeader", "X-Gateway-Trust");
    }
}
