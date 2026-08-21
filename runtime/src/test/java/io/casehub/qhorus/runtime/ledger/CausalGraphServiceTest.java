package io.casehub.qhorus.runtime.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.channel.Channel;

class CausalGraphServiceTest {

    private MessageLedgerEntryRepository repo;
    private ChannelStore channelStore;
    private CausalGraphService service;

    private static final String TID = TenancyConstants.DEFAULT_TENANT_ID;

    @BeforeEach
    void setup() {
        repo = mock(MessageLedgerEntryRepository.class);
        channelStore = mock(ChannelStore.class);
        service = new CausalGraphService();
        service.ledgerRepo = repo;
        service.channelStore = channelStore;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static MessageLedgerEntry testEntry(UUID channelId, String type, String actorId,
            String correlationId, UUID causedBy, Instant occurredAt) {
        MessageLedgerEntry e = new MessageLedgerEntry();
        e.id = UUID.randomUUID();
        e.subjectId = channelId;
        e.channelId = channelId;
        e.messageType = type;
        e.actorId = actorId;
        e.correlationId = correlationId;
        e.causedByEntryId = causedBy;
        e.occurredAt = occurredAt;
        e.content = "test content for " + type;
        return e;
    }

    private static Channel channel(UUID id, String name) {
        return Channel.builder(name).id(id).build();
    }

    @SuppressWarnings("unchecked")
    private void stubChannels(Channel... channels) {
        when(channelStore.findByIds(any(Collection.class)))
                .thenReturn(List.of(channels));
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void buildGraph_linearChain_producesCorrectNodesAndEdges() {
        UUID ch1 = UUID.randomUUID();
        String corrId = UUID.randomUUID().toString();
        Instant t0 = Instant.parse("2026-08-21T10:00:00Z");

        MessageLedgerEntry command = testEntry(ch1, "COMMAND", "agent-a", corrId, null, t0);
        MessageLedgerEntry done = testEntry(ch1, "DONE", "agent-b", corrId, command.id, t0.plusSeconds(5));

        when(repo.findByCorrelationIdAcrossChannels(corrId, 100, TID))
                .thenReturn(List.of(command, done));
        stubChannels(channel(ch1, "work"));

        CausalGraphService.CausalGraph graph = service.buildGraph(corrId, 100, TID);

        assertThat(graph.correlationId()).isEqualTo(corrId);
        assertThat(graph.rootEntryId()).isEqualTo(command.id.toString());
        assertThat(graph.channelCount()).isEqualTo(1);
        assertThat(graph.channels()).containsExactly("work");
        assertThat(graph.outcome()).isEqualTo("FULFILLED");
        assertThat(graph.totalDurationMs()).isEqualTo(5000L);
        assertThat(graph.truncated()).isFalse();
        assertThat(graph.nodes()).hasSize(2);
        assertThat(graph.edges()).hasSize(1);

        CausalGraphService.GraphEdge edge = graph.edges().get(0);
        assertThat(edge.from()).isEqualTo(command.id.toString());
        assertThat(edge.to()).isEqualTo(done.id.toString());
        assertThat(edge.elapsedMs()).isEqualTo(5000L);
        assertThat(edge.type()).isEqualTo("CAUSED_BY");

        assertThat(graph.nodes().stream().filter(n -> n.depth() == 0).count()).isEqualTo(1);
        assertThat(graph.nodes().stream().filter(n -> n.depth() == 1).count()).isEqualTo(1);
    }

    @Test
    void buildGraph_crossChannel_multipleChannelsReported() {
        UUID ch1 = UUID.randomUUID();
        UUID ch2 = UUID.randomUUID();
        String corrId = UUID.randomUUID().toString();
        Instant t0 = Instant.parse("2026-08-21T10:00:00Z");

        MessageLedgerEntry command = testEntry(ch1, "COMMAND", "agent-a", corrId, null, t0);
        MessageLedgerEntry handoff = testEntry(ch1, "HANDOFF", "agent-a", corrId, command.id, t0.plusSeconds(2));
        MessageLedgerEntry done = testEntry(ch2, "DONE", "agent-b", corrId, handoff.id, t0.plusSeconds(7));

        when(repo.findByCorrelationIdAcrossChannels(corrId, 100, TID))
                .thenReturn(List.of(command, handoff, done));
        stubChannels(channel(ch1, "work"), channel(ch2, "ops"));

        CausalGraphService.CausalGraph graph = service.buildGraph(corrId, 100, TID);

        assertThat(graph.channelCount()).isEqualTo(2);
        assertThat(graph.channels()).containsExactlyInAnyOrder("work", "ops");
        assertThat(graph.outcome()).isEqualTo("FULFILLED");
        assertThat(graph.totalDurationMs()).isEqualTo(7000L);
        assertThat(graph.edges()).hasSize(2);
    }

    @Test
    void buildGraph_branchingDelegation_failedPrecedence() {
        UUID ch1 = UUID.randomUUID();
        UUID ch2 = UUID.randomUUID();
        UUID ch3 = UUID.randomUUID();
        String corrId = UUID.randomUUID().toString();
        Instant t0 = Instant.parse("2026-08-21T10:00:00Z");

        MessageLedgerEntry command = testEntry(ch1, "COMMAND", "agent-a", corrId, null, t0);
        MessageLedgerEntry done = testEntry(ch2, "DONE", "agent-b", corrId, command.id, t0.plusSeconds(3));
        MessageLedgerEntry failure = testEntry(ch3, "FAILURE", "agent-c", corrId, command.id, t0.plusSeconds(5));

        when(repo.findByCorrelationIdAcrossChannels(corrId, 100, TID))
                .thenReturn(List.of(command, done, failure));
        stubChannels(channel(ch1, "work"), channel(ch2, "ops"), channel(ch3, "err"));

        CausalGraphService.CausalGraph graph = service.buildGraph(corrId, 100, TID);

        assertThat(graph.outcome()).isEqualTo("FAILED");
        assertThat(graph.totalDurationMs()).isEqualTo(5000L);
    }

    @Test
    void buildGraph_declinedPrecedenceOverFulfilled() {
        UUID ch = UUID.randomUUID();
        String corrId = UUID.randomUUID().toString();
        Instant t0 = Instant.parse("2026-08-21T10:00:00Z");

        MessageLedgerEntry command = testEntry(ch, "COMMAND", "agent-a", corrId, null, t0);
        MessageLedgerEntry done = testEntry(ch, "DONE", "agent-b", corrId, command.id, t0.plusSeconds(3));
        MessageLedgerEntry decline = testEntry(ch, "DECLINE", "agent-c", corrId, command.id, t0.plusSeconds(4));

        when(repo.findByCorrelationIdAcrossChannels(corrId, 100, TID))
                .thenReturn(List.of(command, done, decline));
        stubChannels(channel(ch, "work"));

        CausalGraphService.CausalGraph graph = service.buildGraph(corrId, 100, TID);

        assertThat(graph.outcome()).isEqualTo("DECLINED");
    }

    @Test
    void buildGraph_emptyCorrelation_emptyGraph() {
        when(repo.findByCorrelationIdAcrossChannels(any(), anyInt(), any()))
                .thenReturn(List.of());

        CausalGraphService.CausalGraph graph = service.buildGraph("unknown", 100, TID);

        assertThat(graph.nodes()).isEmpty();
        assertThat(graph.edges()).isEmpty();
        assertThat(graph.outcome()).isEqualTo("OPEN");
        assertThat(graph.rootEntryId()).isNull();
        assertThat(graph.truncated()).isFalse();
    }

    @Test
    void buildGraph_truncated_flagSet() {
        UUID ch = UUID.randomUUID();
        String corrId = UUID.randomUUID().toString();
        Instant t0 = Instant.parse("2026-08-21T10:00:00Z");

        MessageLedgerEntry e1 = testEntry(ch, "COMMAND", "agent-a", corrId, null, t0);
        MessageLedgerEntry e2 = testEntry(ch, "STATUS", "agent-b", corrId, e1.id, t0.plusSeconds(1));
        MessageLedgerEntry e3 = testEntry(ch, "STATUS", "agent-a", corrId, e2.id, t0.plusSeconds(2));

        when(repo.findByCorrelationIdAcrossChannels(corrId, 3, TID))
                .thenReturn(List.of(e1, e2, e3));
        stubChannels(channel(ch, "work"));

        CausalGraphService.CausalGraph graph = service.buildGraph(corrId, 3, TID);

        assertThat(graph.truncated()).isTrue();
    }

    @Test
    void buildGraph_unlinkedEntries_depthMinusOne() {
        UUID ch = UUID.randomUUID();
        String corrId = UUID.randomUUID().toString();
        Instant t0 = Instant.parse("2026-08-21T10:00:00Z");

        MessageLedgerEntry command = testEntry(ch, "COMMAND", "agent-a", corrId, null, t0);
        MessageLedgerEntry status = testEntry(ch, "STATUS", "agent-b", corrId, null, t0.plusSeconds(1));

        when(repo.findByCorrelationIdAcrossChannels(corrId, 100, TID))
                .thenReturn(List.of(command, status));
        stubChannels(channel(ch, "work"));

        CausalGraphService.CausalGraph graph = service.buildGraph(corrId, 100, TID);

        CausalGraphService.GraphNode rootNode = graph.nodes().stream()
                .filter(n -> n.entryId().equals(command.id.toString())).findFirst().orElseThrow();
        CausalGraphService.GraphNode statusNode = graph.nodes().stream()
                .filter(n -> n.entryId().equals(status.id.toString())).findFirst().orElseThrow();

        assertThat(rootNode.depth()).isEqualTo(0);
        assertThat(statusNode.depth()).isEqualTo(-1);
    }

    @Test
    void buildGraph_noTerminal_outcomeOpen() {
        UUID ch = UUID.randomUUID();
        String corrId = UUID.randomUUID().toString();
        Instant t0 = Instant.parse("2026-08-21T10:00:00Z");

        MessageLedgerEntry command = testEntry(ch, "COMMAND", "agent-a", corrId, null, t0);
        MessageLedgerEntry status = testEntry(ch, "STATUS", "agent-b", corrId, command.id, t0.plusSeconds(3));

        when(repo.findByCorrelationIdAcrossChannels(corrId, 100, TID))
                .thenReturn(List.of(command, status));
        stubChannels(channel(ch, "work"));

        CausalGraphService.CausalGraph graph = service.buildGraph(corrId, 100, TID);

        assertThat(graph.outcome()).isEqualTo("OPEN");
        assertThat(graph.totalDurationMs()).isNull();
    }

    @Test
    void buildGraph_handoffIsNonTerminal() {
        UUID ch = UUID.randomUUID();
        String corrId = UUID.randomUUID().toString();
        Instant t0 = Instant.parse("2026-08-21T10:00:00Z");

        MessageLedgerEntry command = testEntry(ch, "COMMAND", "agent-a", corrId, null, t0);
        MessageLedgerEntry handoff = testEntry(ch, "HANDOFF", "agent-a", corrId, command.id, t0.plusSeconds(2));

        when(repo.findByCorrelationIdAcrossChannels(corrId, 100, TID))
                .thenReturn(List.of(command, handoff));
        stubChannels(channel(ch, "work"));

        CausalGraphService.CausalGraph graph = service.buildGraph(corrId, 100, TID);

        assertThat(graph.outcome()).isEqualTo("OPEN");
    }
}
