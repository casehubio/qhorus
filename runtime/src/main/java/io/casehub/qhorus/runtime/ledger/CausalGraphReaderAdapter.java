package io.casehub.qhorus.runtime.ledger;

import io.casehub.qhorus.api.audit.CausalGraph;
import io.casehub.qhorus.api.store.CausalGraphReader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CausalGraphReaderAdapter implements CausalGraphReader {

    @Inject
    CausalGraphService causalGraphService;

    @Override
    @Transactional
    public CausalGraph buildGraph(String correlationId, int limit, String tenancyId) {
        var graph = causalGraphService.buildGraph(correlationId, limit, tenancyId);
        return toApiGraph(graph);
    }

    @Override
    @Transactional
    public String renderGraph(String correlationId, int limit, String tenancyId) {
        var graph = causalGraphService.buildGraph(correlationId, limit, tenancyId);
        return CausalGraphRenderer.render(graph);
    }

    private static CausalGraph toApiGraph(CausalGraphService.CausalGraph g) {
        var nodes = g.nodes().stream()
                .map(n -> new CausalGraph.GraphNode(
                        n.entryId(), n.channelId(), n.channelName(),
                        n.messageType(), n.actorId(), n.occurredAt(),
                        n.content(), n.causedByEntryId(), n.depth()))
                .toList();
        var edges = g.edges().stream()
                .map(e -> new CausalGraph.GraphEdge(
                        e.from(), e.to(), e.type(), e.elapsedMs()))
                .toList();
        return new CausalGraph(
                g.correlationId(), g.rootEntryId(), g.channelCount(),
                g.channels(), g.totalDurationMs(), g.outcome(),
                g.truncated(), nodes, edges);
    }
}
