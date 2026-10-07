package io.casehub.qhorus.cluster;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicOwnershipResolver {

    private final ConsistentHashRing hashRing;
    private final Map<String, String> nodeAddresses;
    private final ConcurrentHashMap<UUID, OwnershipClaim> claims = new ConcurrentHashMap<>();

    public DynamicOwnershipResolver(ConsistentHashRing hashRing, Map<String, String> nodeAddresses) {
        this.hashRing = hashRing;
        this.nodeAddresses = nodeAddresses;
    }

    public NodeInfo owner(UUID channelId) {
        OwnershipClaim claim = claims.get(channelId);
        if (claim != null) {
            return nodeInfo(claim.nodeId());
        }
        return nodeInfo(hashRing.owner(channelId));
    }

    public void updateClaim(UUID channelId, OwnershipClaim claim) {
        claims.put(channelId, claim);
    }

    public void removeClaim(UUID channelId) {
        claims.remove(channelId);
    }

    public void clearClaimsForNode(String nodeId) {
        claims.entrySet().removeIf(e -> e.getValue().nodeId().equals(nodeId));
    }

    public OwnershipClaim getClaim(UUID channelId) {
        return claims.get(channelId);
    }

    public Map<UUID, OwnershipClaim> getAllClaims() {
        return Map.copyOf(claims);
    }

    private NodeInfo nodeInfo(String nodeId) {
        String address = nodeAddresses.get(nodeId);
        if (address == null) {
            address = nodeId + ":8080";
        }
        return new NodeInfo(nodeId, address);
    }
}
