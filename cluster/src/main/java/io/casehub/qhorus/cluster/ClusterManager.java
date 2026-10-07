package io.casehub.qhorus.cluster;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ClusterManager {

    private final String localNodeId;
    private final Map<String, NodeInfo> configuredPeers;
    private final int virtualNodes;
    private final int missThreshold;
    private final boolean quorumEnforced;
    private final Clock clock;
    private final AtomicReference<ConsistentHashRing> ringRef;
    private final ConcurrentHashMap<String, PeerState> peerStates = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ClusterMembershipEvent> pendingEvents = new ConcurrentLinkedQueue<>();
    private DynamicOwnershipResolver resolver;
    private OwnershipEvaluator evaluator;

    public ClusterManager(String localNodeId, Map<String, String> peers,
                          int virtualNodes, int missThreshold,
                          boolean quorumEnforced, Clock clock) {
        this.localNodeId = localNodeId;
        this.virtualNodes = virtualNodes;
        this.missThreshold = missThreshold;
        this.quorumEnforced = quorumEnforced;
        this.clock = clock;

        this.configuredPeers = new LinkedHashMap<>();
        for (var entry : peers.entrySet()) {
            configuredPeers.put(entry.getKey(), new NodeInfo(entry.getKey(), entry.getValue()));
        }

        Set<String> memberIds = new HashSet<>(peers.keySet());
        this.ringRef = new AtomicReference<>(new ConsistentHashRing(memberIds, virtualNodes));

        Instant now = clock.instant();
        for (String peerId : peers.keySet()) {
            if (!peerId.equals(localNodeId)) {
                peerStates.put(peerId, new PeerState(
                        configuredPeers.get(peerId), NodeState.ALIVE, now, 0));
            }
        }
    }

    public NodeInfo owner(UUID channelId) {
        if (resolver != null) {
            return resolver.owner(channelId);
        }
        String nodeId = ringRef.get().owner(channelId);
        NodeInfo info = configuredPeers.get(nodeId);
        if (info == null) {
            return new NodeInfo(nodeId, nodeId + ":8080");
        }
        return info;
    }

    public void setResolver(DynamicOwnershipResolver resolver) {
        this.resolver = resolver;
    }

    public void setEvaluator(OwnershipEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public void updateRemoteOwnership(String peerId, Map<UUID, OwnershipClaim> claims) {
        if (resolver == null) return;
        resolver.clearClaimsForNode(peerId);
        for (var entry : claims.entrySet()) {
            resolver.updateClaim(entry.getKey(), entry.getValue());
        }
    }

    public Map<UUID, OwnershipClaim> getLocalClaims() {
        return evaluator != null ? evaluator.getLocalClaims() : Map.of();
    }

    public void evaluateOwnership() {
        if (evaluator != null) {
            evaluator.evaluate();
        }
    }


    public void clearPeerOwnership(String peerId) {
        if (resolver != null) {
            resolver.clearClaimsForNode(peerId);
        }
    }

    public boolean isLocal(NodeInfo node) {
        return localNodeId.equals(node.nodeId());
    }

    public boolean canServeWrites() {
        if (!quorumEnforced || configuredPeers.size() <= 1) {
            return true;
        }
        long reachable = 1;
        for (PeerState ps : peerStates.values()) {
            if (ps.state() != NodeState.DEAD) {
                reachable++;
            }
        }
        return reachable > configuredPeers.size() / 2;
    }

    public void recordHeartbeat(String nodeId) {
        peerStates.computeIfPresent(nodeId, (id, old) -> {
            Instant now = clock.instant();
            if (old.state() == NodeState.DEAD) {
                emitEvent(id, old.state(), NodeState.ALIVE);
                readdToRing(id);
            } else if (old.state() == NodeState.SUSPECT) {
                emitEvent(id, old.state(), NodeState.ALIVE);
            }
            return new PeerState(old.nodeInfo(), NodeState.ALIVE, now, 0);
        });
    }

    public void recordMiss(String nodeId) {
        peerStates.computeIfPresent(nodeId, (id, old) -> {
            int newMissCount = old.missCount() + 1;
            NodeState newState;
            if (newMissCount >= missThreshold) {
                newState = NodeState.DEAD;
            } else {
                newState = NodeState.SUSPECT;
            }
            if (newState != old.state()) {
                emitEvent(id, old.state(), newState);
                if (newState == NodeState.DEAD) {
                    removeFromRing(id);
                    clearPeerOwnership(id);
                }
            }
            return new PeerState(old.nodeInfo(), newState, old.lastHeartbeat(), newMissCount);
        });
    }

    public void handlePeerDeparture(String nodeId) {
        peerStates.computeIfPresent(nodeId, (id, old) -> {
            if (old.state() != NodeState.DEAD) {
                emitEvent(id, old.state(), NodeState.DEAD);
                removeFromRing(id);
                clearPeerOwnership(id);
            }
            return new PeerState(old.nodeInfo(), NodeState.DEAD, old.lastHeartbeat(), old.missCount());
        });
    }

    public void shutdown(Consumer<NodeInfo> leaveSender) {
        for (var entry : peerStates.entrySet()) {
            if (entry.getValue().state() != NodeState.DEAD) {
                try {
                    leaveSender.accept(entry.getValue().nodeInfo());
                } catch (Exception ignored) {
                }
            }
        }
    }

    public String nodeId() {
        return localNodeId;
    }

    public String ringHash() {
        return ringRef.get().ringHash();
    }

    public int clusterSize() {
        return (int) peerStates.values().stream()
                .filter(p -> p.state() != NodeState.DEAD).count() + 1;
    }

    public int expectedSize() {
        return configuredPeers.size();
    }

    public Map<String, PeerState> peerStates() {
        return Map.copyOf(peerStates);
    }

    public List<ClusterMembershipEvent> drainEvents() {
        List<ClusterMembershipEvent> events = new ArrayList<>();
        ClusterMembershipEvent e;
        while ((e = pendingEvents.poll()) != null) {
            events.add(e);
        }
        return events;
    }

    private void emitEvent(String nodeId, NodeState previous, NodeState next) {
        pendingEvents.add(new ClusterMembershipEvent(nodeId, previous, next, clock.instant()));
    }

    private void removeFromRing(String nodeId) {
        ringRef.updateAndGet(ring -> {
            if (ring.members().contains(nodeId)) {
                return ring.withoutNode(nodeId);
            }
            return ring;
        });
    }

    private void readdToRing(String nodeId) {
        ringRef.updateAndGet(ring -> {
            if (!ring.members().contains(nodeId)) {
                return ring.withNode(nodeId);
            }
            return ring;
        });
    }
}
