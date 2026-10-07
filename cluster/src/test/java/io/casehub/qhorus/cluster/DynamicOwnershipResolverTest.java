package io.casehub.qhorus.cluster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicOwnershipResolverTest {

    private ConsistentHashRing ring;
    private Map<String, String> nodeAddresses;
    private DynamicOwnershipResolver resolver;

    @BeforeEach
    void setUp() {
        ring = new ConsistentHashRing(Set.of("node-1", "node-2", "node-3"), 128);
        nodeAddresses = new LinkedHashMap<>();
        nodeAddresses.put("node-1", "node-1:8080");
        nodeAddresses.put("node-2", "node-2:8080");
        nodeAddresses.put("node-3", "node-3:8080");
        resolver = new DynamicOwnershipResolver(ring, nodeAddresses);
    }

    @Test
    void fallsBackToHashRingWithNoClaims() {
        UUID ch = UUID.randomUUID();
        String expected = ring.owner(ch);
        assertThat(resolver.owner(ch).nodeId()).isEqualTo(expected);
    }

    @Test
    void dynamicClaimOverridesHashRing() {
        UUID ch = UUID.randomUUID();
        String hashOwner = ring.owner(ch);
        String claimant = hashOwner.equals("node-1") ? "node-2" : "node-1";

        resolver.updateClaim(ch, new OwnershipClaim(claimant, 50));
        assertThat(resolver.owner(ch).nodeId()).isEqualTo(claimant);
    }

    @Test
    void removeClaimRevertsToHashRing() {
        UUID ch = UUID.randomUUID();
        String hashOwner = ring.owner(ch);

        resolver.updateClaim(ch, new OwnershipClaim("node-2", 50));
        resolver.removeClaim(ch);
        assertThat(resolver.owner(ch).nodeId()).isEqualTo(hashOwner);
    }

    @Test
    void clearClaimsForNodeRemovesAllClaimsFromPeer() {
        UUID ch1 = UUID.randomUUID();
        UUID ch2 = UUID.randomUUID();
        UUID ch3 = UUID.randomUUID();

        resolver.updateClaim(ch1, new OwnershipClaim("node-2", 10));
        resolver.updateClaim(ch2, new OwnershipClaim("node-2", 20));
        resolver.updateClaim(ch3, new OwnershipClaim("node-3", 30));

        resolver.clearClaimsForNode("node-2");

        assertThat(resolver.getClaim(ch1)).isNull();
        assertThat(resolver.getClaim(ch2)).isNull();
        assertThat(resolver.getClaim(ch3)).isNotNull();
    }

    @Test
    void getAllClaimsReturnsSnapshot() {
        UUID ch = UUID.randomUUID();
        resolver.updateClaim(ch, new OwnershipClaim("node-1", 42));
        Map<UUID, OwnershipClaim> claims = resolver.getAllClaims();
        assertThat(claims).hasSize(1);
        assertThat(claims.get(ch).writeCount()).isEqualTo(42);
    }

    @Test
    void ownerReturnsNodeInfoWithAddress() {
        UUID ch = UUID.randomUUID();
        resolver.updateClaim(ch, new OwnershipClaim("node-2", 10));
        NodeInfo info = resolver.owner(ch);
        assertThat(info.nodeId()).isEqualTo("node-2");
        assertThat(info.address()).isEqualTo("node-2:8080");
    }
}
