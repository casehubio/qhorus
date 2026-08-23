package io.casehub.qhorus.runtime.ledger;

import io.casehub.qhorus.runtime.ledger.CausalGraphService.CausalGraph;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.GraphEdge;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.GraphNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CausalGraphRenderer {

    private static final int MAX_CONTENT_LENGTH = 80;

    private CausalGraphRenderer() {}

    public static String render(CausalGraph graph) {
        StringBuilder sb = new StringBuilder();
        renderHeader(sb, graph);
        if (graph.nodes().isEmpty()) {
            sb.append("\n  (empty — no entries found)\n");
            return sb.toString();
        }
        Map<String, List<ChildEdge>> childrenByParent = buildChildMap(graph);
        GraphNode root = findRoot(graph);
        if (root == null) {
            root = graph.nodes().get(0);
        }
        sb.append('\n');
        renderNode(sb, root, "", true, true, graph, childrenByParent);
        if (graph.truncated()) {
            sb.append("\n  ... (truncated — more entries exist)\n");
        }
        return sb.toString();
    }

    private static void renderHeader(StringBuilder sb, CausalGraph graph) {
        sb.append("Causal Graph — correlation ").append(graph.correlationId());
        sb.append(" (").append(graph.outcome());
        if (graph.totalDurationMs() != null) {
            sb.append(", ").append(formatDuration(graph.totalDurationMs()));
        }
        sb.append(", ").append(graph.channelCount()).append(" channel");
        if (graph.channelCount() != 1) sb.append('s');
        sb.append(')');
    }

    private static void renderNode(StringBuilder sb, GraphNode node, String prefix,
                                    boolean isLast, boolean isRoot, CausalGraph graph,
                                    Map<String, List<ChildEdge>> childrenByParent) {
        String connector = isRoot ? "  " : (isLast ? "└─" : "├─");
        sb.append(prefix).append(connector);
        sb.append('[').append(node.channelName() != null ? node.channelName() : "?").append("] ");
        sb.append(String.format("%-8s", node.messageType()));
        sb.append(' ').append(node.actorId());
        if (node.content() != null && !node.content().isBlank()) {
            sb.append("  \"").append(truncateContent(node.content())).append('"');
        }
        sb.append("  depth=").append(node.depth());

        List<ChildEdge> children = childrenByParent.getOrDefault(node.entryId(), List.of());
        ChildEdge selfEdge = findEdgeToNode(node.entryId(), graph.edges());
        if (selfEdge != null && selfEdge.elapsedMs() != null) {
            sb.append("  +").append(formatDuration(selfEdge.elapsedMs()));
        }
        sb.append('\n');

        String childPrefix = isRoot ? "  " : (prefix + (isLast ? "    " : "│   "));
        for (int i = 0; i < children.size(); i++) {
            ChildEdge child = children.get(i);
            boolean last = (i == children.size() - 1);
            renderNode(sb, child.node(), childPrefix, last, false, graph, childrenByParent);
        }
    }

    private static Map<String, List<ChildEdge>> buildChildMap(CausalGraph graph) {
        Map<String, GraphNode> nodesById = new LinkedHashMap<>();
        for (GraphNode n : graph.nodes()) {
            nodesById.put(n.entryId(), n);
        }
        Map<String, List<ChildEdge>> children = new LinkedHashMap<>();
        for (GraphEdge edge : graph.edges()) {
            GraphNode child = nodesById.get(edge.to());
            if (child != null) {
                children.computeIfAbsent(edge.from(), k -> new ArrayList<>())
                        .add(new ChildEdge(child, edge.elapsedMs()));
            }
        }
        return children;
    }

    private static GraphNode findRoot(CausalGraph graph) {
        if (graph.rootEntryId() != null) {
            return graph.nodes().stream()
                    .filter(n -> n.entryId().equals(graph.rootEntryId()))
                    .findFirst().orElse(null);
        }
        return graph.nodes().stream()
                .filter(n -> n.depth() == 0)
                .findFirst().orElse(null);
    }

    private static ChildEdge findEdgeToNode(String nodeId, List<GraphEdge> edges) {
        return edges.stream()
                .filter(e -> e.to().equals(nodeId))
                .findFirst()
                .map(e -> new ChildEdge(null, e.elapsedMs()))
                .orElse(null);
    }

    private static String truncateContent(String content) {
        if (content.length() <= MAX_CONTENT_LENGTH) return content;
        return content.substring(0, MAX_CONTENT_LENGTH - 3) + "...";
    }

    private static String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        return String.format("%.1fs", ms / 1000.0);
    }

    private record ChildEdge(GraphNode node, Long elapsedMs) {}
}
