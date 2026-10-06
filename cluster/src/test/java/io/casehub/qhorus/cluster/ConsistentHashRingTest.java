package io.casehub.qhorus.cluster;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.withinPercentage;

class ConsistentHashRingTest {

    @Test
    void ownerIsDeterministic() {
        var ring = new ConsistentHashRing(Set.of("node-1", "node-2", "node-3"), 128);
        UUID channelId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        String owner1 = ring.owner(channelId);
        String owner2 = ring.owner(channelId);
        assertThat(owner1).isEqualTo(owner2);
    }

    @Test
    void allChannelsHaveAnOwner() {
        var ring = new ConsistentHashRing(Set.of("node-1", "node-2", "node-3"), 128);
        for (int i = 0; i < 100; i++) {
            assertThat(ring.owner(UUID.randomUUID())).isIn("node-1", "node-2", "node-3");
        }
    }

    @Test
    void distributionIsReasonablyEven() {
        var ring = new ConsistentHashRing(Set.of("node-1", "node-2", "node-3"), 128);
        Map<String, Integer> counts = new HashMap<>();
        int total = 10_000;
        for (int i = 0; i < total; i++) {
            counts.merge(ring.owner(UUID.randomUUID()), 1, Integer::sum);
        }
        double expected = total / 3.0;
        for (int count : counts.values()) {
            assertThat((double) count).isCloseTo(expected, withinPercentage(15));
        }
    }

    @Test
    void withNodeAddsToRing() {
        var ring = new ConsistentHashRing(Set.of("node-1", "node-2"), 128);
        var expanded = ring.withNode("node-3");
        assertThat(expanded.members()).containsExactlyInAnyOrder("node-1", "node-2", "node-3");
        assertThat(ring.members()).containsExactlyInAnyOrder("node-1", "node-2");
    }

    @Test
    void withoutNodeRemovesFromRing() {
        var ring = new ConsistentHashRing(Set.of("node-1", "node-2", "node-3"), 128);
        var shrunk = ring.withoutNode("node-2");
        assertThat(shrunk.members()).containsExactlyInAnyOrder("node-1", "node-3");
    }

    @Test
    void onlyFractionOfChannelsMoveOnNodeRemoval() {
        var ring3 = new ConsistentHashRing(Set.of("node-1", "node-2", "node-3"), 128);
        var ring2 = ring3.withoutNode("node-2");
        List<UUID> channels = new ArrayList<>();
        for (int i = 0; i < 1000; i++) channels.add(UUID.randomUUID());
        long moved = channels.stream()
                .filter(ch -> !ring3.owner(ch).equals(ring2.owner(ch)))
                .count();
        assertThat(moved).isBetween(200L, 500L);
    }

    @Test
    void ringHashIsDeterministic() {
        var ring1 = new ConsistentHashRing(Set.of("node-1", "node-2"), 128);
        var ring2 = new ConsistentHashRing(Set.of("node-2", "node-1"), 128);
        assertThat(ring1.ringHash()).isEqualTo(ring2.ringHash());
    }

    @Test
    void ringHashChangesWithMembership() {
        var ring2 = new ConsistentHashRing(Set.of("node-1", "node-2"), 128);
        var ring3 = ring2.withNode("node-3");
        assertThat(ring2.ringHash()).isNotEqualTo(ring3.ringHash());
    }

    @Test
    void singleNodeOwnsEverything() {
        var ring = new ConsistentHashRing(Set.of("node-1"), 128);
        for (int i = 0; i < 100; i++) {
            assertThat(ring.owner(UUID.randomUUID())).isEqualTo("node-1");
        }
    }

    @Test
    void emptyRingThrows() {
        assertThatThrownBy(() -> new ConsistentHashRing(Set.of(), 128))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
