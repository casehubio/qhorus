package io.casehub.qhorus.api.spi.causal;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.util.List;
import java.util.Map;

@McpDomain("qhorus/causal-graph")
public interface CausalGraphApi {

    @PlatformQuery("Build a causal graph for a correlation chain")
    Map<String, Object> getGraph(@PathParam String correlationId, Integer limit);

    @PlatformQuery("Get attribution chain for a ledger entry")
    List<Map<String, Object>> getAttribution(@PathParam String entryId);
}
