package io.casehub.qhorus.message;

import io.casehub.qhorus.api.channel.EnforcementMode;
import io.casehub.qhorus.api.message.EnforcementBlockedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnforcementBlockedExceptionTest {

    private static io.casehub.qhorus.api.spi.DispatchAdvisory warning(String source, String msg) {
        return new io.casehub.qhorus.api.spi.DispatchAdvisory(source,
                                                              io.casehub.qhorus.api.spi.Severity.WARNING, msg, java.util.Map.of(),
                                                              io.casehub.qhorus.api.spi.SuggestedAction.LOG);
    }

    @Test
    void extendsIllegalStateException() {
        var ex = new EnforcementBlockedException(
                EnforcementMode.BLOCKING,
                List.of("REQUEST_RESPONSE"),
                List.of(warning("REQUEST_RESPONSE", "too many open queries")),
                false);
        assertThat(ex).isInstanceOf(IllegalStateException.class);
        assertThat(ex.mode()).isEqualTo(EnforcementMode.BLOCKING);
        assertThat(ex.severityUpgrade()).isFalse();
        assertThat(ex.effectiveMode()).isEqualTo(EnforcementMode.BLOCKING);
        assertThat(ex.violationSources()).containsExactly("REQUEST_RESPONSE");
        assertThat(ex.violations()).hasSize(1);
        assertThat(ex.getMessage()).contains("BLOCKING");
    }

    @Test
    void quarantineModeInMessage() {
        var ex = new EnforcementBlockedException(
                EnforcementMode.QUARANTINE,
                List.of("TYPE_POLICY", "CORRELATION_INTEGRITY"),
                List.of(warning("TYPE_POLICY", "violation 1"),
                        warning("CORRELATION_INTEGRITY", "violation 2")),
                false);
        assertThat(ex.getMessage()).contains("QUARANTINE");
        assertThat(ex.violationSources()).containsExactly("TYPE_POLICY", "CORRELATION_INTEGRITY");
        assertThat(ex.violations()).hasSize(2);
    }

    @Test
    void severityUpgradeReportsEffectiveMode() {
        var ex = new EnforcementBlockedException(
                EnforcementMode.ADVISORY,
                List.of("TYPE_POLICY"),
                List.of(new io.casehub.qhorus.api.spi.DispatchAdvisory("TYPE_POLICY",
                                                                       io.casehub.qhorus.api.spi.Severity.CRITICAL, "critical violation",
                                                                       java.util.Map.of(), io.casehub.qhorus.api.spi.SuggestedAction.LOG)),
                true);
        assertThat(ex.mode()).isEqualTo(EnforcementMode.ADVISORY);
        assertThat(ex.severityUpgrade()).isTrue();
        assertThat(ex.effectiveMode()).isEqualTo(EnforcementMode.BLOCKING);
        assertThat(ex.getMessage()).contains("BLOCKING");
    }

    @Test
    void listsAreDefensivelyCopied() {
        var sources    = new java.util.ArrayList<>(List.of("A"));
        var violations = new java.util.ArrayList<>(List.of(warning("A", "v1")));
        var ex         = new EnforcementBlockedException(EnforcementMode.BLOCKING, sources, violations, false);
        sources.add("B");
        violations.add(warning("B", "v2"));
        assertThat(ex.violationSources()).containsExactly("A");
        assertThat(ex.violations()).hasSize(1);
    }
}
