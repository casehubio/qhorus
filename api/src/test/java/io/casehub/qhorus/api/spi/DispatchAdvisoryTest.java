package io.casehub.qhorus.api.spi;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DispatchAdvisoryTest {

    @Test
    void constructsWithAllFields() {
        var advisory = new DispatchAdvisory("REQUEST_RESPONSE", Severity.WARNING,
                "3 open queries", Map.of("count", 3), SuggestedAction.LOG);
        assertThat(advisory.source()).isEqualTo("REQUEST_RESPONSE");
        assertThat(advisory.severity()).isEqualTo(Severity.WARNING);
        assertThat(advisory.message()).isEqualTo("3 open queries");
        assertThat(advisory.evidence()).containsEntry("count", 3);
        assertThat(advisory.suggestedAction()).isEqualTo(SuggestedAction.LOG);
    }

    @Test
    void nullSourceThrows() {
        assertThatThrownBy(() -> new DispatchAdvisory(null, Severity.WARNING, "msg", Map.of(), SuggestedAction.LOG))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullSeverityThrows() {
        assertThatThrownBy(() -> new DispatchAdvisory("SRC", null, "msg", Map.of(), SuggestedAction.LOG))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullMessageThrows() {
        assertThatThrownBy(() -> new DispatchAdvisory("SRC", Severity.WARNING, null, Map.of(), SuggestedAction.LOG))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullEvidenceDefaultsToEmptyMap() {
        var advisory = new DispatchAdvisory("SRC", Severity.WARNING, "msg", null, SuggestedAction.LOG);
        assertThat(advisory.evidence()).isEmpty();
    }

    @Test
    void nullActionDefaultsToLog() {
        var advisory = new DispatchAdvisory("SRC", Severity.WARNING, "msg", Map.of(), null);
        assertThat(advisory.suggestedAction()).isEqualTo(SuggestedAction.LOG);
    }

    @Test
    void evidenceIsImmutableCopy() {
        var mutable = new HashMap<String, Object>();
        mutable.put("key", "val");
        var advisory = new DispatchAdvisory("SRC", Severity.WARNING, "msg", mutable, SuggestedAction.LOG);
        mutable.put("key2", "val2");
        assertThat(advisory.evidence()).doesNotContainKey("key2");
    }

    @Test
    void severityOrdering() {
        assertThat(Severity.CRITICAL.isAtLeast(Severity.WARNING)).isTrue();
        assertThat(Severity.WARNING.isAtLeast(Severity.CRITICAL)).isFalse();
        assertThat(Severity.ADVISORY.isAtLeast(Severity.ADVISORY)).isTrue();
        assertThat(Severity.CRITICAL.isAtLeast(Severity.ADVISORY)).isTrue();
        assertThat(Severity.ADVISORY.isAtLeast(Severity.WARNING)).isFalse();
    }

    @Test
    void allSuggestedActionValues() {
        assertThat(SuggestedAction.values()).containsExactly(
                SuggestedAction.LOG, SuggestedAction.ESCALATE,
                SuggestedAction.INVESTIGATE, SuggestedAction.REROUTE);
    }
}
