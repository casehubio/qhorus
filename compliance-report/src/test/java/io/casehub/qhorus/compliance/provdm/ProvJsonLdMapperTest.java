package io.casehub.qhorus.compliance.provdm;

import io.casehub.qhorus.runtime.ledger.CausalGraphService.CausalGraph;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.GraphEdge;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.GraphNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProvJsonLdMapperTest {

    final UUID entryId1 = UUID.randomUUID();
    final UUID entryId2 = UUID.randomUUID();
    final UUID channelId1 = UUID.randomUUID();
    final UUID channelId2 = UUID.randomUUID();

    @Test
    @SuppressWarnings("unchecked")
    void toProvJsonLd_containsCorrectContext() {
        Map<String, Object> result = ProvJsonLdMapper.toProvJsonLd(twoNodeGraph());

        assertThat(result).containsKey("@context");
        var context = (Map<String, Object>) result.get("@context");
        assertThat(context).containsEntry("prov", "http://www.w3.org/ns/prov#");
        assertThat(context).containsEntry("ledger", "https://casehubio.github.io/ledger#");
        assertThat(context).containsEntry("qhorus", "https://casehubio.github.io/qhorus#");
        assertThat(context).containsEntry("xsd", "http://www.w3.org/2001/XMLSchema#");
    }

    @Test
    @SuppressWarnings("unchecked")
    void toProvJsonLd_mapsAgentsToProvAgent() {
        Map<String, Object> result = ProvJsonLdMapper.toProvJsonLd(twoNodeGraph());

        var agents = (Map<String, Object>) result.get("agent");
        assertThat(agents).containsKey("ledger:actor/agent-alpha");
        assertThat(agents).containsKey("ledger:actor/agent-beta");
    }

    @Test
    @SuppressWarnings("unchecked")
    void toProvJsonLd_mapsCommandToProvActivity() {
        Map<String, Object> result = ProvJsonLdMapper.toProvJsonLd(twoNodeGraph());

        var activities = (Map<String, Object>) result.get("activity");
        String activityKey = "ledger:activity/" + entryId1;
        assertThat(activities).containsKey(activityKey);

        var activity = (Map<String, Object>) activities.get(activityKey);
        assertThat(activity).containsEntry("prov:type", "COMMAND");
        assertThat(activity).containsKey("prov:startTime");
    }

    @Test
    @SuppressWarnings("unchecked")
    void toProvJsonLd_mapsDelegationToActedOnBehalfOf() {
        GraphNode commander = new GraphNode(
                entryId1.toString(), channelId1.toString(), "ch-1",
                "COMMAND", "agent-alpha", "2026-08-27T10:00:00Z",
                "do X", null, 0);
        GraphNode delegate = new GraphNode(
                entryId2.toString(), channelId1.toString(), "ch-1",
                "HANDOFF", "agent-alpha", "2026-08-27T10:01:00Z",
                "delegating", entryId1.toString(), 1);
        GraphEdge edge = new GraphEdge(
                entryId1.toString(), entryId2.toString(), "CAUSED_BY", 60000L);
        CausalGraph graph = new CausalGraph(
                "corr-1", entryId1.toString(), 1, List.of("ch-1"),
                60000L, "FULFILLED", false,
                List.of(commander, delegate), List.of(edge));

        Map<String, Object> result = ProvJsonLdMapper.toProvJsonLd(graph);

        var delegation = (Map<String, Object>) result.get("actedOnBehalfOf");
        assertThat(delegation).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void toProvJsonLd_mapsCausalEdgesToWasDerivedFrom() {
        Map<String, Object> result = ProvJsonLdMapper.toProvJsonLd(twoNodeGraph());

        var derivations = (Map<String, Object>) result.get("wasDerivedFrom");
        assertThat(derivations).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void toProvJsonLd_mapsChannelToProvLocation() {
        Map<String, Object> result = ProvJsonLdMapper.toProvJsonLd(twoNodeGraph());

        var activities = (Map<String, Object>) result.get("activity");
        String activityKey = "ledger:activity/" + entryId1;
        var activity = (Map<String, Object>) activities.get(activityKey);
        assertThat(activity).containsEntry("prov:atLocation", "qhorus:channel/" + channelId1);
    }

    @Test
    void toProvJsonLd_emptyGraph_returnsMinimalDocument() {
        CausalGraph empty = new CausalGraph(
                "corr-1", null, 0, List.of(), null, "OPEN",
                false, List.of(), List.of());

        Map<String, Object> result = ProvJsonLdMapper.toProvJsonLd(empty);

        assertThat(result).containsKey("@context");
        assertThat((Map<?, ?>) result.get("agent")).isEmpty();
        assertThat((Map<?, ?>) result.get("activity")).isEmpty();
    }

    private CausalGraph twoNodeGraph() {
        GraphNode node1 = new GraphNode(
                entryId1.toString(), channelId1.toString(), "command-channel",
                "COMMAND", "agent-alpha", "2026-08-27T10:00:00Z",
                "analyze data", null, 0);
        GraphNode node2 = new GraphNode(
                entryId2.toString(), channelId2.toString(), "response-channel",
                "DONE", "agent-beta", "2026-08-27T10:05:00Z",
                "analysis complete", entryId1.toString(), 1);
        GraphEdge edge = new GraphEdge(
                entryId1.toString(), entryId2.toString(), "CAUSED_BY", 300_000L);

        return new CausalGraph(
                "corr-1", entryId1.toString(), 2,
                List.of("command-channel", "response-channel"),
                300_000L, "FULFILLED", false,
                List.of(node1, node2), List.of(edge));
    }
}
