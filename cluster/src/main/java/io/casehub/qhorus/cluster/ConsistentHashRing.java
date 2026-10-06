package io.casehub.qhorus.cluster;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ConsistentHashRing {

    private final NavigableMap<Long, String> ring;
    private final int virtualNodes;
    private final Set<String> members;
    private final String ringHash;

    public ConsistentHashRing(Set<String> members, int virtualNodes) {
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("Ring must have at least one member");
        }
        this.virtualNodes = virtualNodes;
        this.members = Set.copyOf(members);
        this.ring = buildRing(this.members, virtualNodes);
        this.ringHash = computeRingHash(this.members);
    }

    public String owner(UUID channelId) {
        long hash = hash(channelId.toString().getBytes(StandardCharsets.UTF_8));
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
        return entry != null ? entry.getValue() : ring.firstEntry().getValue();
    }

    public ConsistentHashRing withNode(String nodeId) {
        var expanded = new HashSet<>(members);
        expanded.add(nodeId);
        return new ConsistentHashRing(expanded, virtualNodes);
    }

    public ConsistentHashRing withoutNode(String nodeId) {
        var shrunk = new HashSet<>(members);
        shrunk.remove(nodeId);
        return new ConsistentHashRing(shrunk, virtualNodes);
    }

    public Set<String> members() {
        return members;
    }

    public String ringHash() {
        return ringHash;
    }

    static long hash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            long h = 0;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (digest[i] & 0xFF);
            }
            return h;
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 not available", e);
        }
    }

    private static NavigableMap<Long, String> buildRing(Set<String> members, int vNodes) {
        NavigableMap<Long, String> ring = new TreeMap<>();
        for (String nodeId : members) {
            for (int i = 0; i < vNodes; i++) {
                byte[] key = (nodeId + "#" + i).getBytes(StandardCharsets.UTF_8);
                ring.put(hash(key), nodeId);
            }
        }
        return Collections.unmodifiableNavigableMap(ring);
    }

    private static String computeRingHash(Set<String> members) {
        String sorted = members.stream().sorted().collect(Collectors.joining(","));
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(sorted.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 not available", e);
        }
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
