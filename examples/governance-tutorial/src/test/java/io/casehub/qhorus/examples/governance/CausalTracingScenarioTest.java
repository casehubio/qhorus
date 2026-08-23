package io.casehub.qhorus.examples.governance;

import io.casehub.qhorus.api.channel.ChannelDetail;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.CausalGraph;
import io.casehub.qhorus.runtime.mcp.QhorusMcpTools;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario 1 — Cross-channel causal tracing.
 *
 * Demonstrates #398: an agent receives a COMMAND on a work channel,
 * delegates via HANDOFF to a specialist on an ops channel, and the
 * specialist completes with DONE. The causal graph traces the full
 * attribution chain across both channels.
 */
@QuarkusTest
class CausalTracingScenarioTest {

    @Inject QhorusMcpTools tools;

    @Test
    @TestTransaction
    void crossChannelCausalGraph() {
        // --- Setup: two channels, two agents ---
        tools.createChannel("gov-work", "Work coordination channel", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tools.createChannel("gov-ops", "Operations execution channel", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tools.register("agent-coordinator", "Coordinates work", List.of("coordination"), null, null);
        tools.register("agent-analyst", "Performs analysis", List.of("analysis"), null, null);

        // --- Act: COMMAND → HANDOFF → DONE across channels ---
        // 1. Coordinator sends COMMAND on work channel
        DispatchResult command = tools.sendMessage("gov-work", "agent-coordinator", "COMMAND",
                "Analyze the Q3 dataset and report findings", null, null, null, null, null, null, null, null, null);
        String corrId = command.correlationId();
        assertThat(corrId).isNotNull();

        // 2. Coordinator delegates to analyst via HANDOFF (same channel, targets ops agent)
        DispatchResult handoff = tools.sendMessage("gov-work", "agent-coordinator", "HANDOFF",
                null, null, corrId, command.messageId(), null,
                "instance:agent-analyst", null, null, command.ledgerEntryId() != null ? command.ledgerEntryId().toString() : null, null);

        // 3. Analyst completes on ops channel with DONE
        DispatchResult done = tools.sendMessage("gov-ops", "agent-analyst", "DONE",
                "Analysis complete: Q3 revenue up 12%, costs stable", null, corrId, command.messageId(), null, null, null, null,
                handoff.ledgerEntryId() != null ? handoff.ledgerEntryId().toString() : null, null);

        // --- Verify: causal graph shows full attribution ---
        CausalGraph graph = tools.getCausalGraph(corrId, null);
        assertThat(graph.nodes()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(graph.channelCount()).isEqualTo(2);
        assertThat(graph.channels()).contains("gov-work", "gov-ops");

        // Verify outcome
        String outcome = graph.outcome();
        assertThat(outcome).isIn("FULFILLED", "OPEN");

        // --- Render as text and print (the tutorial output) ---
        String rendered = tools.renderCausalGraph(corrId, null);
        System.out.println("\n=== Scenario 1: Cross-Channel Causal Tracing ===\n");
        System.out.println(rendered);

        // Verify rendered output contains key elements
        assertThat(rendered).contains("gov-work");
        assertThat(rendered).contains("gov-ops");
        assertThat(rendered).contains("COMMAND");
        assertThat(rendered).contains("HANDOFF");
        assertThat(rendered).contains("DONE");
        assertThat(rendered).contains("agent-coordinator");
        assertThat(rendered).contains("agent-analyst");
        assertThat(rendered).contains("2 channels");
    }
}
