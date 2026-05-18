package com.example.purchaseconversion.infrastructure.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Readiness contributor per R-008 mitigation: refuse UP when
 * {@code WEX_GATEWAY_REQUIRED=true} is set in the environment but the
 * deployment has not configured an upstream trusted gateway.
 *
 * <p>v1 verification is presence of the env var {@code WEX_GATEWAY_TRUST_HEADER}.
 * Production deployments are expected to configure the upstream ingress to inject
 * a trust header (e.g., {@code X-Gateway-Trust: <signed-token>}); the readiness
 * probe asserts the configuration is present, not the runtime validity of each
 * inbound request (that's a Phase-11 deliverable per OQ-010).
 */
@Component
public class GatewayRequiredHealthIndicator implements HealthIndicator {

    private final boolean required;
    private final String trustHeaderName;

    public GatewayRequiredHealthIndicator(
            @Value("${WEX_GATEWAY_REQUIRED:false}") boolean required,
            @Value("${WEX_GATEWAY_TRUST_HEADER:}") String trustHeaderName) {
        this.required = required;
        this.trustHeaderName = trustHeaderName == null ? "" : trustHeaderName.trim();
    }

    @Override
    public Health health() {
        if (!required) {
            return Health.up()
                    .withDetail("gatewayRequired", false)
                    .withDetail("reason", "WEX_GATEWAY_REQUIRED unset or false")
                    .build();
        }
        if (trustHeaderName.isEmpty()) {
            return Health.down()
                    .withDetail("gatewayRequired", true)
                    .withDetail("reason", "WEX_GATEWAY_TRUST_HEADER not configured")
                    .build();
        }
        return Health.up()
                .withDetail("gatewayRequired", true)
                .withDetail("trustHeader", trustHeaderName)
                .build();
    }
}
