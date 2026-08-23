package io.casehub.qhorus.message;

import io.casehub.qhorus.api.channel.EnforcementMode;
import io.casehub.qhorus.api.message.EnforcementBlockedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnforcementBlockedExceptionTest {

    @Test
    void extendsIllegalStateException() {
        var ex = new EnforcementBlockedException(
                EnforcementMode.BLOCKING,
                List.of("REQUEST_RESPONSE"),
                List.of("[REQUEST_RESPONSE] too many open queries"));
        assertThat(ex).isInstanceOf(IllegalStateException.class);
        assertThat(ex.mode()).isEqualTo(EnforcementMode.BLOCKING);
        assertThat(ex.violationSources()).containsExactly("REQUEST_RESPONSE");
        assertThat(ex.violations()).hasSize(1);
        assertThat(ex.getMessage()).contains("BLOCKING");
    }

    @Test
    void quarantineModeInMessage() {
        var ex = new EnforcementBlockedException(
                EnforcementMode.QUARANTINE,
                List.of("TYPE_POLICY", "CORRELATION_INTEGRITY"),
                List.of("violation 1", "violation 2"));
        assertThat(ex.getMessage()).contains("QUARANTINE");
        assertThat(ex.violationSources()).containsExactly("TYPE_POLICY", "CORRELATION_INTEGRITY");
        assertThat(ex.violations()).hasSize(2);
    }

    @Test
    void listsAreDefensivelyCopied() {
        var sources = new java.util.ArrayList<>(List.of("A"));
        var violations = new java.util.ArrayList<>(List.of("v1"));
        var ex = new EnforcementBlockedException(EnforcementMode.BLOCKING, sources, violations);
        sources.add("B");
        violations.add("v2");
        assertThat(ex.violationSources()).containsExactly("A");
        assertThat(ex.violations()).containsExactly("v1");
    }
}
