package io.casehub.qhorus.runtime.channel;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {

    private static final long WINDOW_SECONDS = 60L;

    private final ConcurrentHashMap<UUID, Deque<Instant>> channelWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> instanceWindows = new ConcurrentHashMap<>();

    public String check(UUID channelId, String channelName, String sender, Integer limitPerChannel,
            Integer limitPerInstance) {
        Instant now = Instant.now();

        if (limitPerChannel != null) {
            Deque<Instant> window = channelWindows.computeIfAbsent(channelId, k -> new ArrayDeque<>());
            synchronized (window) {
                pruneOlderThan(window, now);
                if (window.size() >= limitPerChannel) {
                    return "Rate limit exceeded for channel '" + channelName
                            + "': max " + limitPerChannel + " messages per minute across all senders.";
                }
            }
        }

        if (limitPerInstance != null) {
            String key = channelId + ":" + sender;
            Deque<Instant> window = instanceWindows.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (window) {
                pruneOlderThan(window, now);
                if (window.size() >= limitPerInstance) {
                    return "Rate limit exceeded for sender '" + sender
                            + "': max " + limitPerInstance + " messages per minute on channel '" + channelName + "'.";
                }
            }
        }

        return null;
    }

    public void recordSend(UUID channelId, String sender, Integer limitPerChannel, Integer limitPerInstance) {
        Instant now = Instant.now();

        if (limitPerChannel != null) {
            Deque<Instant> window = channelWindows.computeIfAbsent(channelId, k -> new ArrayDeque<>());
            synchronized (window) {
                window.addLast(now);
            }
        }

        if (limitPerInstance != null) {
            String key = channelId + ":" + sender;
            Deque<Instant> window = instanceWindows.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (window) {
                window.addLast(now);
            }
        }
    }

    private void pruneOlderThan(Deque<Instant> window, Instant now) {
        Instant cutoff = now.minusSeconds(WINDOW_SECONDS);
        while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
            window.pollFirst();
        }
    }
}
