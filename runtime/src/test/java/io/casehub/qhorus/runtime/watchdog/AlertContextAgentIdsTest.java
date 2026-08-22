package io.casehub.qhorus.runtime.watchdog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.watchdog.*;

class AlertContextAgentIdsTest {

    @Test
    void loopDetected_returnsSender() {
        var ctx = new LoopDetectedContext(UUID.randomUUID(), "ch", "agent-a", 5, 0.95);
        assertThat(ctx.affectedAgentIds()).containsExactly("agent-a");
    }

    @Test
    void agentStale_returnsStaleIds() {
        var ctx = new AgentStaleContext(2, List.of("agent-a", "agent-b"));
        assertThat(ctx.affectedAgentIds()).containsExactly("agent-a", "agent-b");
    }

    @Test
    void contextPressure_returnsActorId() {
        var ctx = new ContextPressureContext(UUID.randomUUID(), "ch", "agent-x", 85);
        assertThat(ctx.affectedAgentIds()).containsExactly("agent-x");
    }

    @Test
    void echoChamber_returnsParticipants() {
        var ctx = new EchoChamberContext(UUID.randomUUID(), "ch", List.of("a", "b", "c"), 0.9);
        assertThat(ctx.affectedAgentIds()).containsExactly("a", "b", "c");
    }

    @Test
    void channelIdle_returnsEmpty() {
        var ctx = new ChannelIdleContext(List.of("ch"), 300L);
        assertThat(ctx.affectedAgentIds()).isEmpty();
    }

    @Test
    void queueDepth_returnsEmpty() {
        var ctx = new QueueDepthContext("ch", 50, 100);
        assertThat(ctx.affectedAgentIds()).isEmpty();
    }
}
