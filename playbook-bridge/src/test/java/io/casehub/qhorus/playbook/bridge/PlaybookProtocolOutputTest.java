package io.casehub.qhorus.playbook.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.Severity;
import io.casehub.qhorus.api.spi.SuggestedAction;

class PlaybookProtocolOutputTest {

    @Test
    void validAdvisory_mapsCorrectly() {
        Map<String, Object> output = Map.of("advisories", List.of(
                Map.of("severity", "WARNING", "message", "too many open queries",
                       "action", "LOG")));
        List<DispatchAdvisory> result =
                PlaybookProtocolOutput.parse(output, "approval-gate");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).source()).isEqualTo("approval-gate");
        assertThat(result.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(result.get(0).message()).isEqualTo("too many open queries");
        assertThat(result.get(0).suggestedAction()).isEqualTo(SuggestedAction.LOG);
    }

    @Test
    void missingAdvisoriesKey_returnsEmpty() {
        List<DispatchAdvisory> result =
                PlaybookProtocolOutput.parse(Map.of(), "gate");
        assertThat(result).isEmpty();
    }

    @Test
    void invalidSeverity_producesValidationAdvisory() {
        Map<String, Object> output = Map.of("advisories", List.of(
                Map.of("severity", "INVALID", "message", "test")));
        List<DispatchAdvisory> result =
                PlaybookProtocolOutput.parse(output, "gate");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).source())
                .isEqualTo("playbook-bridge:validation");
        assertThat(result.get(0).severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void missingMessage_producesValidationAdvisory() {
        Map<String, Object> output = Map.of("advisories", List.of(
                Map.of("severity", "WARNING")));
        List<DispatchAdvisory> result =
                PlaybookProtocolOutput.parse(output, "gate");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).source())
                .isEqualTo("playbook-bridge:validation");
    }

    @Test
    void defaultAction_isLOG() {
        Map<String, Object> output = Map.of("advisories", List.of(
                Map.of("severity", "ADVISORY", "message", "info")));
        List<DispatchAdvisory> result =
                PlaybookProtocolOutput.parse(output, "gate");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).suggestedAction())
                .isEqualTo(SuggestedAction.LOG);
    }

    @Test
    void evidenceMap_passedThrough() {
        Map<String, Object> evidence = Map.of("count", 5);
        Map<String, Object> output = Map.of("advisories", List.of(
                Map.of("severity", "WARNING", "message", "over limit",
                       "evidence", evidence)));
        List<DispatchAdvisory> result =
                PlaybookProtocolOutput.parse(output, "gate");
        assertThat(result.get(0).evidence()).containsEntry("count", 5);
    }
}
