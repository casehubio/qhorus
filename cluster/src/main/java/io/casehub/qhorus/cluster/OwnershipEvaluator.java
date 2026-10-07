package io.casehub.qhorus.cluster;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OwnershipEvaluator {

    private final String localNodeId;
    private final WriteFrequencyTracker tracker;
    private final DynamicOwnershipResolver resolver;
    private final double hysteresisRatio;
    private final int minClaimWrites;
    private final ConcurrentHashMap<UUID, OwnershipClaim> localClaims = new ConcurrentHashMap<>();

    public OwnershipEvaluator(String localNodeId, WriteFrequencyTracker tracker,
                              DynamicOwnershipResolver resolver,
                              double hysteresisRatio, int minClaimWrites) {
        this.localNodeId = localNodeId;
        this.tracker = tracker;
        this.resolver = resolver;
        this.hysteresisRatio = hysteresisRatio;
        this.minClaimWrites = minClaimWrites;
    }

    public void evaluate() {
        tracker.rotateAll();

        var claimIterator = localClaims.entrySet().iterator();
        while (claimIterator.hasNext()) {
            var entry = claimIterator.next();
            UUID channelId = entry.getKey();
            long localCount = tracker.getCount(channelId);
            if (localCount == 0) {
                claimIterator.remove();
                resolver.removeClaim(channelId);
            } else {
                OwnershipClaim updated = new OwnershipClaim(localNodeId, localCount);
                localClaims.put(channelId, updated);
                resolver.updateClaim(channelId, updated);
            }
        }

        for (UUID channelId : tracker.getActiveChannels()) {
            if (localClaims.containsKey(channelId)) {
                continue;
            }
            long localCount = tracker.getCount(channelId);
            if (localCount < minClaimWrites) {
                continue;
            }
            NodeInfo currentOwner = resolver.owner(channelId);
            if (currentOwner.nodeId().equals(localNodeId)) {
                continue;
            }
            OwnershipClaim ownerClaim = resolver.getClaim(channelId);
            long ownerCount = ownerClaim != null ? ownerClaim.writeCount() : 0;
            if (localCount > hysteresisRatio * ownerCount) {
                OwnershipClaim claim = new OwnershipClaim(localNodeId, localCount);
                localClaims.put(channelId, claim);
                resolver.updateClaim(channelId, claim);
            }
        }
    }

    public Map<UUID, OwnershipClaim> getLocalClaims() {
        return Map.copyOf(localClaims);
    }

    void addLocalClaim(UUID channelId, OwnershipClaim claim) {
        localClaims.put(channelId, claim);
    }
}
