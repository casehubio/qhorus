package io.casehub.qhorus.graphql.causal;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.audit.CausalGraph;
import io.casehub.qhorus.api.audit.LedgerEntryView;
import io.casehub.qhorus.api.spi.causal.CausalGraphApi;
import io.casehub.qhorus.api.store.CausalGraphReader;
import io.casehub.qhorus.api.store.LedgerReader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class CausalGraphService implements CausalGraphApi {

    @Inject
    CausalGraphReader causalGraphReader;

    @Inject
    LedgerReader ledgerReader;

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    @Transactional
    public Map<String, Object> getGraph(String correlationId, Integer limit) {
        int effectiveLimit = Math.min(Math.max(limit != null ? limit : 100, 1), 500);
        CausalGraph graph = causalGraphReader.buildGraph(
                correlationId, effectiveLimit, currentPrincipal.tenancyId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("correlationId", graph.correlationId());
        result.put("rootEntryId", graph.rootEntryId());
        result.put("channelCount", graph.channelCount());
        result.put("channels", graph.channels());
        result.put("totalDurationMs", graph.totalDurationMs());
        result.put("outcome", graph.outcome());
        result.put("truncated", graph.truncated());

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (CausalGraph.GraphNode n : graph.nodes()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("entryId", n.entryId());
            node.put("channelId", n.channelId());
            node.put("channelName", n.channelName());
            node.put("messageType", n.messageType());
            node.put("actorId", n.actorId());
            node.put("occurredAt", n.occurredAt());
            node.put("content", n.content());
            node.put("causedByEntryId", n.causedByEntryId());
            node.put("depth", n.depth());
            nodes.add(node);
        }
        result.put("nodes", nodes);

        List<Map<String, Object>> edges = new ArrayList<>();
        for (CausalGraph.GraphEdge e : graph.edges()) {
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("from", e.from());
            edge.put("to", e.to());
            edge.put("type", e.type());
            edge.put("elapsedMs", e.elapsedMs());
            edges.add(edge);
        }
        result.put("edges", edges);

        return result;
    }

    @Override
    @Transactional
    public List<Map<String, Object>> getAttribution(String entryId) {
        UUID entryUuid = UUID.fromString(entryId);

        List<LedgerEntryView> chain = ledgerReader.findAncestorChainCrossChannel(
                entryUuid, currentPrincipal.tenancyId());

        return chain.stream().map(e -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("entryId", e.entryId().toString());
            map.put("channelId", e.channelId() != null ? e.channelId().toString() : "");
            map.put("channelName", e.channelName() != null ? e.channelName() : "unknown");
            map.put("messageType", e.messageType());
            map.put("actorId", e.actorId() != null ? e.actorId() : "");
            map.put("correlationId", e.correlationId() != null ? e.correlationId() : "");
            map.put("occurredAt", e.occurredAt() != null ? e.occurredAt().toString() : "");
            map.put("content", e.content() != null ? e.content() : "");
            map.put("causedByEntryId", e.causedByEntryId() != null ? e.causedByEntryId().toString() : "");
            return map;
        }).toList();
    }
}
