package io.casehub.qhorus.cluster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OwnershipEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private static final String LOCAL_NODE = "node-1";
    private static final double HYSTERESIS = 2.0;
    private static final int MIN_CLAIMS = 5;

    private WriteFrequencyTracker tracker;
    private DynamicOwnershipResolver resolver;
    private OwnershipEvaluator evaluator;
    private ConsistentHashRing ring;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        tracker = new WriteFrequencyTracker(10, Duration.ofSeconds(30), clock);
        ring = new ConsistentHashRing(Set.of("node-1", "node-2", "node-3"), 128);
        Map<String, String> addresses = new LinkedHashMap<>();
        addresses.put("node-1", "node-1:8080");
        addresses.put("node-2", "node-2:8080");
        addresses.put("node-3", "node-3:8080");
        resolver = new DynamicOwnershipResolver(ring, addresses);
        evaluator = new OwnershipEvaluator(LOCAL_NODE, tracker, resolver, HYSTERESIS, MIN_CLAIMS);
    }

    private UUID channelOwnedByRemote() {
        for (int i = 0; i < 1000; i++) {
            UUID ch = UUID.randomUUID();
            if (!ring.owner(ch).equals(LOCAL_NODE)) {
                return ch;
            }
        }
        throw new AssertionError("Could not find a channel owned by a remote node");
    }

    private UUID channelOwnedLocally() {
        for (int i = 0; i < 1000; i++) {
            UUID ch = UUID.randomUUID();
            if (ring.owner(ch).equals(LOCAL_NODE)) {
                return ch;
            }
        }
        throw new AssertionError("Could not find a channel owned locally");
    }

    @Test
    void claimsChannelWhenWritesExceedHashRingOwnerWithNoWrites() {
        UUID ch = channelOwnedByRemote();
        for (int i = 0; i < 10; i++) {
            tracker.recordWrite(ch);
        }
        evaluator.evaluate();
        assertThat(evaluator.getLocalClaims()).containsKey(ch);
        assertThat(evaluator.getLocalClaims().get(ch).writeCount()).isEqualTo(10);
    }

    @Test
    void doesNotClaimWhenBelowMinClaimWrites() {
        UUID ch = channelOwnedByRemote();
        for (int i = 0; i < 3; i++) {
            tracker.recordWrite(ch);
        }
        evaluator.evaluate();
        assertThat(evaluator.getLocalClaims()).isEmpty();
    }

    @Test
    void doesNotClaimWhenOwnerWriteCountTooHigh() {
        UUID ch = channelOwnedByRemote();
        resolver.updateClaim(ch, new OwnershipClaim("node-2", 100));
        for (int i = 0; i < 50; i++) {
            tracker.recordWrite(ch);
        }
        evaluator.evaluate();
        assertThat(resolver.getClaim(ch).nodeId()).isEqualTo("node-2");
    }

    @Test
    void claimsWhenExceedingRemoteOwnerByHysteresis() {
        UUID ch = channelOwnedByRemote();
        resolver.updateClaim(ch, new OwnershipClaim("node-2", 10));
        for (int i = 0; i < 25; i++) {
            tracker.recordWrite(ch);
        }
        evaluator.evaluate();
        assertThat(evaluator.getLocalClaims()).containsKey(ch);
        assertThat(resolver.getClaim(ch).nodeId()).isEqualTo(LOCAL_NODE);
    }

    @Test
    void relinquishesWhenWriteCountDropsToZero() {
        UUID ch = channelOwnedByRemote();
        resolver.updateClaim(ch, new OwnershipClaim(LOCAL_NODE, 50));
        evaluator.addLocalClaim(ch, new OwnershipClaim(LOCAL_NODE, 50));

        evaluator.evaluate();

        assertThat(evaluator.getLocalClaims()).doesNotContainKey(ch);
        assertThat(resolver.getClaim(ch)).isNull();
    }

    @Test
    void doesNotRelinquishWhenStillWriting() {
        UUID ch = channelOwnedByRemote();
        resolver.updateClaim(ch, new OwnershipClaim(LOCAL_NODE, 50));
        evaluator.addLocalClaim(ch, new OwnershipClaim(LOCAL_NODE, 50));
        tracker.recordWrite(ch);

        evaluator.evaluate();

        assertThat(evaluator.getLocalClaims()).containsKey(ch);
    }

    @Test
    void skipsChannelsAlreadyOwnedLocallyByHashRing() {
        UUID ch = channelOwnedLocally();
        for (int i = 0; i < 10; i++) {
            tracker.recordWrite(ch);
        }
        evaluator.evaluate();
        assertThat(evaluator.getLocalClaims()).doesNotContainKey(ch);
    }
}
