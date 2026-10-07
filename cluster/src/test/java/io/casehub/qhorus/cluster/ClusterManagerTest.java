package io.casehub.qhorus.cluster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterManagerTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneId.of("UTC"));
    }

    private ClusterManager managerWithPeers(String localNodeId, String... peers) {
        Map<String, String> peerMap = new LinkedHashMap<>();
        for (String p : peers) {
            peerMap.put(p, p + ":8080");
        }
        return new ClusterManager(localNodeId, peerMap, 128, 2, true, clock);
    }

    @Test
    void ownerReturnsSameNodeForSameChannel() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2", "node-3");
        UUID ch = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        assertThat(mgr.owner(ch).nodeId()).isEqualTo(mgr.owner(ch).nodeId());
    }

    @Test
    void isLocalReturnsTrueForLocalNode() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2", "node-3");
        UUID ch = UUID.randomUUID();
        NodeInfo owner = mgr.owner(ch);
        if (owner.nodeId().equals("node-1")) {
            assertThat(mgr.isLocal(owner)).isTrue();
        } else {
            assertThat(mgr.isLocal(owner)).isFalse();
        }
    }

    @Test
    void canServeWritesWithAllAlive() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2", "node-3");
        assertThat(mgr.canServeWrites()).isTrue();
    }

    @Test
    void canServeWritesAfterOneDead() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2", "node-3");
        mgr.recordMiss("node-2");
        mgr.recordMiss("node-2");
        assertThat(mgr.canServeWrites()).isTrue();
    }

    @Test
    void cannotServeWritesAfterTwoDead() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2", "node-3");
        mgr.recordMiss("node-2");
        mgr.recordMiss("node-2");
        mgr.recordMiss("node-3");
        mgr.recordMiss("node-3");
        assertThat(mgr.canServeWrites()).isFalse();
    }

    @Test
    void heartbeatRecoversDeadNode() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2", "node-3");
        mgr.recordMiss("node-2");
        mgr.recordMiss("node-2");
        assertThat(mgr.peerStates().get("node-2").state()).isEqualTo(NodeState.DEAD);
        mgr.recordHeartbeat("node-2");
        assertThat(mgr.peerStates().get("node-2").state()).isEqualTo(NodeState.ALIVE);
    }

    @Test
    void missTransitionsToSuspectThenDead() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2", "node-3");
        assertThat(mgr.peerStates().get("node-2").state()).isEqualTo(NodeState.ALIVE);
        mgr.recordMiss("node-2");
        assertThat(mgr.peerStates().get("node-2").state()).isEqualTo(NodeState.SUSPECT);
        mgr.recordMiss("node-2");
        assertThat(mgr.peerStates().get("node-2").state()).isEqualTo(NodeState.DEAD);
    }

    @Test
    void deadNodeRemovedFromRing() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2", "node-3");
        mgr.recordMiss("node-2");
        mgr.recordMiss("node-2");
        for (int i = 0; i < 100; i++) {
            assertThat(mgr.owner(UUID.randomUUID()).nodeId()).isIn("node-1", "node-3");
        }
    }

    @Test
    void recoveredNodeReaddedToRing() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2", "node-3");
        mgr.recordMiss("node-2");
        mgr.recordMiss("node-2");
        mgr.recordHeartbeat("node-2");
        boolean hasNode2 = false;
        for (int i = 0; i < 1000; i++) {
            if (mgr.owner(UUID.randomUUID()).nodeId().equals("node-2")) {
                hasNode2 = true;
                break;
            }
        }
        assertThat(hasNode2).isTrue();
    }

    @Test
    void handlePeerDepartureRemovesImmediately() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2", "node-3");
        mgr.handlePeerDeparture("node-2");
        assertThat(mgr.peerStates().get("node-2").state()).isEqualTo(NodeState.DEAD);
    }

    @Test
    void quorumDisabledWhenSingleNode() {
        var mgr = managerWithPeers("node-1", "node-1");
        assertThat(mgr.canServeWrites()).isTrue();
    }

    @Test
    void shutdownSendsLeaveToAllAlivePeers() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2", "node-3");
        List<String> leaveSent = new ArrayList<>();
        mgr.shutdown(node -> leaveSent.add(node.nodeId()));
        assertThat(leaveSent).containsExactlyInAnyOrder("node-2", "node-3");
    }

    @Test
    void shutdownSkipsDeadPeers() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2", "node-3");
        mgr.recordMiss("node-2");
        mgr.recordMiss("node-2");
        List<String> leaveSent = new ArrayList<>();
        mgr.shutdown(node -> leaveSent.add(node.nodeId()));
        assertThat(leaveSent).containsExactly("node-3");
    }

    @Test
    void membershipEventsRecorded() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2", "node-3");
        mgr.recordMiss("node-2");
        mgr.recordMiss("node-2");
        List<ClusterMembershipEvent> events = mgr.drainEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).newState()).isEqualTo(NodeState.SUSPECT);
        assertThat(events.get(1).newState()).isEqualTo(NodeState.DEAD);
    }

    @Test
    void evaluateOwnershipDelegatesToEvaluator() {
        var mgr     = managerWithPeers("node-1", "node-1", "node-2");
        var ring    = new ConsistentHashRing(java.util.Set.of("node-1", "node-2"), 128);
        var peerMap = new LinkedHashMap<String, String>();
        peerMap.put("node-1", "node-1:8080");
        peerMap.put("node-2", "node-2:8080");
        var resolver = new DynamicOwnershipResolver(ring, peerMap);
        mgr.setResolver(resolver);

        var tracker   = new WriteFrequencyTracker(10, java.time.Duration.ofSeconds(30), clock);
        var evaluator = new OwnershipEvaluator("node-1", tracker, resolver, 2.0, 5);
        mgr.setEvaluator(evaluator);

        UUID channelId = UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            tracker.recordWrite(channelId);
        }

        mgr.evaluateOwnership();

        assertThat(evaluator.getLocalClaims()).containsKey(channelId);
    }

    @Test
    void evaluateOwnershipNoOpWithoutEvaluator() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2");
        mgr.evaluateOwnership();
    }
}
