package io.casehub.qhorus.cluster;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@IfBuildProperty(name = "casehub.qhorus.relay.enabled", stringValue = "true",
                 enableIfMissing = false)
public class ClusterHealthResource {

    private final ClusterManager clusterManager;

    public ClusterHealthResource(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }

    @GET
    @Path("/health/cluster")
    public ClusterHealthResponse health() {
        return new ClusterHealthResponse(
                clusterManager.nodeId(),
                clusterManager.canServeWrites() ? "UP" : "DEGRADED",
                clusterManager.clusterSize(),
                clusterManager.expectedSize(),
                clusterManager.ringHash(),
                clusterManager.peerStates());
    }

    @GET
    @Path("/admin/topology")
    public TopologyResponse topology() {
        List<TopologyResponse.NodeSummary> nodes = new ArrayList<>();
        nodes.add(new TopologyResponse.NodeSummary(
                clusterManager.nodeId(), "self", NodeState.ALIVE, null));
        for (var entry : clusterManager.peerStates().entrySet()) {
            PeerState ps = entry.getValue();
            nodes.add(new TopologyResponse.NodeSummary(
                    entry.getKey(), ps.nodeInfo().address(),
                    ps.state(), ps.lastHeartbeat()));
        }
        return new TopologyResponse(nodes);
    }

    @GET
    @Path("/health/ownership")
    public OwnershipHealthResponse ownership() {
        var claims = clusterManager.getLocalClaims();
        return new OwnershipHealthResponse(
                clusterManager.nodeId(),
                claims.size(),
                claims);
    }

}
