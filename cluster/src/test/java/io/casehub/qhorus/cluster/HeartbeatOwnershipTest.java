package io.casehub.qhorus.cluster;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HeartbeatOwnershipTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));

    private ClusterManager managerWithPeers(String localNodeId, String... peers) {
        Map<String, String> peerMap = new LinkedHashMap<>();
        for (String p : peers) {
            peerMap.put(p, p + ":8080");
        }
        return new ClusterManager(localNodeId, peerMap, 128, 2, true, CLOCK);
    }

    private DynamicOwnershipResolver resolverFor(String... nodes) {
        ConsistentHashRing ring = new ConsistentHashRing(Set.of(nodes), 128);
        Map<String, String> addresses = new LinkedHashMap<>();
        for (String n : nodes) {
            addresses.put(n, n + ":8080");
        }
        return new DynamicOwnershipResolver(ring, addresses);
    }

    @Test
    void heartbeatResponseIncludesOwnershipClaims() {
        UUID ch = UUID.randomUUID();
        var claims = Map.of(ch, new OwnershipClaim("node-1", 42));
        var resp = new HeartbeatResponse("node-1", NOW, "abc", "UP", claims);
        assertThat(resp.ownershipClaims()).containsKey(ch);
        assertThat(resp.ownershipClaims().get(ch).writeCount()).isEqualTo(42);
    }

    @Test
    void backwardCompatibleConstructorSetsEmptyClaims() {
        var resp = new HeartbeatResponse("node-1", NOW, "abc", "UP");
        assertThat(resp.ownershipClaims()).isEmpty();
    }

    @Test
    void updateRemoteOwnershipMergesPeerClaims() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2", "node-3");
        var resolver = resolverFor("node-1", "node-2", "node-3");
        mgr.setResolver(resolver);

        UUID ch = UUID.randomUUID();
        mgr.updateRemoteOwnership("node-2", Map.of(ch, new OwnershipClaim("node-2", 100)));

        assertThat(mgr.owner(ch).nodeId()).isEqualTo("node-2");
    }

    @Test
    void peerDeathClearsOwnershipClaims() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2", "node-3");
        var resolver = resolverFor("node-1", "node-2", "node-3");
        mgr.setResolver(resolver);

        UUID ch = UUID.randomUUID();
        mgr.updateRemoteOwnership("node-2", Map.of(ch, new OwnershipClaim("node-2", 50)));
        assertThat(mgr.owner(ch).nodeId()).isEqualTo("node-2");

        mgr.recordMiss("node-2");
        mgr.recordMiss("node-2");

        assertThat(resolver.getClaim(ch)).isNull();
    }

    @Test
    void heartbeatServicePropagatesOwnershipOnTick() {
        var mgr = managerWithPeers("node-1", "node-1", "node-2");
        var resolver = resolverFor("node-1", "node-2");
        mgr.setResolver(resolver);

        UUID ch = UUID.randomUUID();
        var remoteClaims = Map.of(ch, new OwnershipClaim("node-2", 75));
        var heartbeatService = new HeartbeatService(mgr,
                peer -> new HeartbeatResponse("node-2", NOW, mgr.ringHash(), "UP", remoteClaims));

        heartbeatService.tick();

        assertThat(mgr.owner(ch).nodeId()).isEqualTo("node-2");
    }
}
