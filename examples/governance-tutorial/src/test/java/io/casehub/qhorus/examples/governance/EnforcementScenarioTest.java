package io.casehub.qhorus.examples.governance;

import io.casehub.qhorus.api.message.EnforcementBlockedException;
import io.casehub.qhorus.testing.QhorusTestHelper;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scenario 3 — Active governance policies (enforcement modes).
 *
 * Demonstrates #400: enforcement mode configured on a channel promotes
 * advisory violations to blocking rejections. REQUEST_RESPONSE protocol
 * advisories become hard blocks in BLOCKING mode, and QUARANTINE mode
 * additionally pauses the channel.
 */
@QuarkusTest
class EnforcementScenarioTest {

    @Inject QhorusTestHelper helper;

    @Test
    @TestTransaction
    void blockingModeRejectsProtocolViolation() {
        // --- Setup: channel with REQUEST_RESPONSE protocol + BLOCKING enforcement ---
        helper.createChannel("gov-enforce-ch", "Enforcement test channel", null, null, null,
                null, null, null, null, null, null, null,
                "REQUEST_RESPONSE", null, null, null, null, null, null);
        helper.register("agent-eager", "Eager agent that sends too many queries", null, null, null);
        helper.setEnforcementMode("gov-enforce-ch", "BLOCKING");

        // --- Act: send queries up to and past the threshold (max-open-queries=2) ---
        // Query 1 — ok
        helper.sendMessage("gov-enforce-ch", "agent-eager", "QUERY",
                "What is the current status?", null, null, null, null, null, null, null, null, null);
        // Query 2 — ok (at threshold)
        helper.sendMessage("gov-enforce-ch", "agent-eager", "QUERY",
                "What are the latest metrics?", null, null, null, null, null, null, null, null, null);

        // Query 3 — should be BLOCKED (exceeds max-open-queries=2)
        System.out.println("\n=== Scenario 3: Active Governance Policies ===\n");
        assertThatThrownBy(() ->
                helper.sendMessage("gov-enforce-ch", "agent-eager", "QUERY",
                        "What is the forecast?", null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasCauseInstanceOf(EnforcementBlockedException.class)
                .satisfies(ex -> {
                    var cause = (EnforcementBlockedException) ex.getCause();
                    assertThat(cause.mode().name()).isEqualTo("BLOCKING");
                    assertThat(cause.violationSources()).contains("REQUEST_RESPONSE");
                    System.out.println("BLOCKED: " + cause.getMessage());
                    System.out.println("Violation sources: " + cause.violationSources());
                });

        // Channel should NOT be paused in BLOCKING mode
        var detail = helper.listChannels().stream()
                .filter(cd -> "gov-enforce-ch".equals(cd.name()))
                .findFirst().orElseThrow();
        assertThat(detail.paused()).isFalse();
        System.out.println("Channel paused: " + detail.paused() + " (BLOCKING = reject only, no containment)");
    }

    @Test
    @TestTransaction
    void quarantineModeBlocksAndPausesChannel() {
        // --- Setup: channel with REQUEST_RESPONSE + QUARANTINE ---
        helper.createChannel("gov-quarantine-ch", "Quarantine test channel", null, null, null,
                null, null, null, null, null, null, null,
                "REQUEST_RESPONSE", null, null, null, null, null, null);
        helper.register("agent-reckless", "Reckless agent", null, null, null);
        helper.setEnforcementMode("gov-quarantine-ch", "QUARANTINE");

        // Send 2 queries (at threshold)
        helper.sendMessage("gov-quarantine-ch", "agent-reckless", "QUERY",
                "First query", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("gov-quarantine-ch", "agent-reckless", "QUERY",
                "Second query", null, null, null, null, null, null, null, null, null);

        // Query 3 — triggers QUARANTINE
        assertThatThrownBy(() ->
                helper.sendMessage("gov-quarantine-ch", "agent-reckless", "QUERY",
                        "Third query — this triggers quarantine", null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasCauseInstanceOf(EnforcementBlockedException.class)
                .satisfies(ex -> {
                    var cause = (EnforcementBlockedException) ex.getCause();
                    assertThat(cause.mode().name()).isEqualTo("QUARANTINE");
                    System.out.println("\nQUARANTINED: " + cause.getMessage());
                });

        // Channel should be paused after QUARANTINE containment
        var detail = helper.listChannels().stream()
                .filter(cd -> "gov-quarantine-ch".equals(cd.name()))
                .findFirst().orElseThrow();
        assertThat(detail.paused()).isTrue();
        System.out.println("Channel paused: " + detail.paused() + " (QUARANTINE = reject + pause + expire)");
    }

    @Test
    @TestTransaction
    void exclusionsKeepSourceAdvisoryOnly() {
        // --- Setup: channel with BLOCKING + REQUEST_RESPONSE excluded ---
        helper.createChannel("gov-exclude-ch", "Exclusion test channel", null, null, null,
                null, null, null, null, null, null, null,
                "REQUEST_RESPONSE", null, null, null, null, null, null);
        helper.register("agent-free", "Agent with excluded source", null, null, null);
        helper.setEnforcementMode("gov-exclude-ch", "BLOCKING");
        helper.setEnforcementExclusions("gov-exclude-ch", "REQUEST_RESPONSE");

        // Exceed threshold — but REQUEST_RESPONSE is excluded, so no block
        helper.sendMessage("gov-exclude-ch", "agent-free", "QUERY",
                "Query 1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("gov-exclude-ch", "agent-free", "QUERY",
                "Query 2", null, null, null, null, null, null, null, null, null);
        // Query 3 — passes because REQUEST_RESPONSE is excluded from enforcement
        var result = helper.sendMessage("gov-exclude-ch", "agent-free", "QUERY",
                "Query 3 — passes with exclusion", null, null, null, null, null, null, null, null, null);
        assertThat(result.advisories()).isNotEmpty();
        System.out.println("\nExclusion test: Query 3 dispatched with advisory (not blocked).");
        System.out.println("Advisories: " + result.advisories());
    }
}
