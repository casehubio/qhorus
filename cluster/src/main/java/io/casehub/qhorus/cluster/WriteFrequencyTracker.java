package io.casehub.qhorus.cluster;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

class WriteFrequencyTracker {

    private final int bucketCount;
    private final Duration bucketDuration;
    private Clock clock;
    private final ConcurrentHashMap<UUID, BucketWindow> windows = new ConcurrentHashMap<>();

    public WriteFrequencyTracker(int bucketCount, Duration bucketDuration, Clock clock) {
        this.bucketCount = bucketCount;
        this.bucketDuration = bucketDuration;
        this.clock = clock;
    }

    public void recordWrite(UUID channelId) {
        windows.computeIfAbsent(channelId, id -> new BucketWindow(bucketCount, bucketDuration, clock))
                .recordWrite();
    }

    public long getCount(UUID channelId) {
        BucketWindow window = windows.get(channelId);
        return window == null ? 0 : window.getCount();
    }

    public Set<UUID> getActiveChannels() {
        return Set.copyOf(windows.keySet());
    }

    public void rotateAll() {
        var iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            entry.getValue().tryRotate();
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    public int channelCount() {
        return windows.size();
    }

    void setClock(Clock clock) {
        this.clock = clock;
        for (BucketWindow window : windows.values()) {
            window.setClock(clock);
        }
    }
}
