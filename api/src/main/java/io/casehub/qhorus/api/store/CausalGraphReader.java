package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.audit.CausalGraph;

public interface CausalGraphReader {

    CausalGraph buildGraph(String correlationId, int limit, String tenancyId);

    String renderGraph(String correlationId, int limit, String tenancyId);
}
