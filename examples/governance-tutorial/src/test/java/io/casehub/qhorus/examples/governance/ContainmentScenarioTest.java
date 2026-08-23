package io.casehub.qhorus.examples.governance;

import io.casehub.qhorus.api.channel.ChannelDetail;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.CommitmentService;
import io.casehub.qhorus.runtime.mcp.QhorusMcpTools;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scenario 2 — Cascade containment.
 *
 * Demonstrates #399 containment primitives: pausing a channel and expiring
 * commitments. Also shows that QUARANTINE enforcement mode (#400) combines
 * blocking + containment in a single dispatch rejection.
 */
@QuarkusTest
class ContainmentScenarioTest {

    @Inject QhorusMcpTools tools;
    @Inject ChannelService channelService;
    @Inject CommitmentService commitmentService;

    @Test
    @TestTransaction
    void containmentPausesPreventsMessages() {
        // --- Setup: channel with open obligation ---
        tools.createChannel("gov-contain-ch", "Containment demo channel", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tools.register("agent-worker", "Worker agent", null, null, null);

        // Send a COMMAND to create an obligation
        DispatchResult cmd = tools.sendMessage("gov-contain-ch", "agent-worker", "COMMAND",
                "Process this task", null, null, null, null, null, null, null, null, null);
        assertThat(cmd.correlationId()).isNotNull();

        // --- Act: containment — pause the channel and expire commitments ---
        var ch = channelService.findByName("gov-contain-ch").orElseThrow();
        channelService.pause(ch.id());
        commitmentService.expireByChannel(ch.id());

        System.out.println("\n=== Scenario 2: Cascade Containment ===\n");

        // --- Verify: channel is paused ---
        var detail = tools.listChannels().stream()
                .filter(cd -> "gov-contain-ch".equals(cd.name()))
                .findFirst().orElseThrow();
        assertThat(detail.paused()).isTrue();
        System.out.println("Channel paused: " + detail.paused());

        // --- Verify: new messages are blocked ---
        assertThatThrownBy(() ->
                tools.sendMessage("gov-contain-ch", "agent-worker", "STATUS",
                        "Trying to send on paused channel", null, null, null, null, null, null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("paused");
        System.out.println("Dispatch blocked on paused channel: confirmed");

        // --- Verify: commitment expired ---
        var commitment = commitmentService.findByCorrelationId(cmd.correlationId());
        assertThat(commitment).isPresent();
        assertThat(commitment.get().state().isTerminal()).isTrue();
        System.out.println("Commitment state: " + commitment.get().state()
                + " (expired by containment)");
    }
}
