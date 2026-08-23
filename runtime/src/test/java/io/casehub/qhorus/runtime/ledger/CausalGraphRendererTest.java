package io.casehub.qhorus.runtime.ledger;

import io.casehub.qhorus.runtime.ledger.CausalGraphService.CausalGraph;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.GraphEdge;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.GraphNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CausalGraphRendererTest {

    @Test
    void emptyGraph() {
        var graph = new CausalGraph("corr-1", null, 0, List.of(), null, "OPEN", false,
                List.of(), List.of());
        String result = CausalGraphRenderer.render(graph);
        assertThat(result).contains("corr-1").contains("OPEN").contains("empty");
    }

    @Test
    void singleNodeGraph() {
        var node = new GraphNode("e1", "ch1", "work", "COMMAND", "agent-a",
                "2026-08-23T10:00:00Z", "Analyze the dataset", null, 0);
        var graph = new CausalGraph("corr-1", "e1", 1, List.of("work"), 0L, "OPEN", false,
                List.of(node), List.of());
        String result = CausalGraphRenderer.render(graph);
        assertThat(result).contains("corr-1");
        assertThat(result).contains("[work]");
        assertThat(result).contains("COMMAND");
        assertThat(result).contains("agent-a");
        assertThat(result).contains("Analyze the dataset");
    }

    @Test
    void linearChain() {
        var n1 = new GraphNode("e1", "ch1", "work", "COMMAND", "agent-a",
                "2026-08-23T10:00:00Z", "Do work", null, 0);
        var n2 = new GraphNode("e2", "ch1", "work", "STATUS", "agent-a",
                "2026-08-23T10:00:01Z", "Working on it", "e1", 1);
        var n3 = new GraphNode("e3", "ch1", "work", "DONE", "agent-a",
                "2026-08-23T10:00:05Z", "Complete", "e1", 1);
        var edges = List.of(
                new GraphEdge("e1", "e2", "CAUSED_BY", 1000L),
                new GraphEdge("e1", "e3", "CAUSED_BY", 5000L));
        var graph = new CausalGraph("corr-1", "e1", 1, List.of("work"), 5000L,
                "FULFILLED", false, List.of(n1, n2, n3), edges);
        String result = CausalGraphRenderer.render(graph);
        assertThat(result).contains("FULFILLED").contains("5.0s");
        assertThat(result).contains("COMMAND").contains("STATUS").contains("DONE");
    }

    @Test
    void crossChannelTree() {
        var n1 = new GraphNode("e1", "ch1", "work", "COMMAND", "agent-a",
                "2026-08-23T10:00:00Z", "Analyze", null, 0);
        var n2 = new GraphNode("e2", "ch1", "work", "HANDOFF", "agent-a",
                "2026-08-23T10:00:02Z", null, "e1", 1);
        var n3 = new GraphNode("e3", "ch2", "ops", "DONE", "agent-b",
                "2026-08-23T10:00:05Z", "Analysis complete", "e1", 2);
        var edges = List.of(
                new GraphEdge("e1", "e2", "CAUSED_BY", 2000L),
                new GraphEdge("e1", "e3", "CAUSED_BY", 5000L));
        var graph = new CausalGraph("corr-1", "e1", 2, List.of("work", "ops"), 5000L,
                "FULFILLED", false, List.of(n1, n2, n3), edges);
        String result = CausalGraphRenderer.render(graph);
        assertThat(result).contains("2 channels");
        assertThat(result).contains("[work]").contains("[ops]");
        assertThat(result).contains("agent-a").contains("agent-b");
    }

    @Test
    void truncatedGraphShowsIndicator() {
        var n1 = new GraphNode("e1", "ch1", "work", "COMMAND", "agent-a",
                "2026-08-23T10:00:00Z", "Start", null, 0);
        var graph = new CausalGraph("corr-1", "e1", 1, List.of("work"), null,
                "OPEN", true, List.of(n1), List.of());
        String result = CausalGraphRenderer.render(graph);
        assertThat(result).contains("truncated");
    }

    @Test
    void longContentIsTruncated() {
        String longContent = "A".repeat(200);
        var n1 = new GraphNode("e1", "ch1", "work", "COMMAND", "agent-a",
                "2026-08-23T10:00:00Z", longContent, null, 0);
        var graph = new CausalGraph("corr-1", "e1", 1, List.of("work"), null,
                "OPEN", false, List.of(n1), List.of());
        String result = CausalGraphRenderer.render(graph);
        assertThat(result).doesNotContain(longContent);
        assertThat(result).contains("...");
    }
}
