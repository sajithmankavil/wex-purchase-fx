package com.example.purchaseconversion.application.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpstreamBadResponseExceptionTest {

    @Test
    @DisplayName("single-arg constructor: message carries the reason, no cause")
    void singleArgConstructor() {
        UpstreamBadResponseException ex = new UpstreamBadResponseException("schema_invalid");

        assertThat(ex.getReason()).isEqualTo("schema_invalid");
        assertThat(ex.getMessage()).isEqualTo("treasury bad response: schema_invalid");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("two-arg constructor: wires the upstream cause through for diagnosis")
    void twoArgConstructorWiresCause() {
        RuntimeException upstreamFailure = new RuntimeException("connection reset");

        UpstreamBadResponseException ex = new UpstreamBadResponseException("rate_sanity", upstreamFailure);

        assertThat(ex.getReason()).isEqualTo("rate_sanity");
        assertThat(ex.getMessage()).isEqualTo("treasury bad response: rate_sanity");
        assertThat(ex.getCause()).isSameAs(upstreamFailure);
    }

    @Test
    @DisplayName("single-arg constructor rejects null reason")
    void singleArgRejectsNullReason() {
        assertThatThrownBy(() -> new UpstreamBadResponseException(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("two-arg constructor rejects null reason")
    void twoArgRejectsNullReason() {
        assertThatThrownBy(() -> new UpstreamBadResponseException(null, new RuntimeException("x")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("is a DomainException so it maps through ProblemDetailExceptionHandler")
    void isADomainException() {
        assertThat(new UpstreamBadResponseException("orientation_drift")).isInstanceOf(DomainException.class);
    }
}
