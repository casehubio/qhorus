package io.casehub.qhorus.cluster;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class BucketWindow {

    private final AtomicLong[] buckets;
    private final long bucketDurationMillis;
    private Clock clock;
    private int currentIndex;
    private Instant currentBucketStart;

    public BucketWindow(int bucketCount, Duration bucketDuration, Clock clock) {
        this.buckets = new AtomicLong[bucketCount];
        for (int i = 0; i < bucketCount; i++) {
            this.buckets[i] = new AtomicLong(0);
        }
        this.bucketDurationMillis = bucketDuration.toMillis();
        this.clock = clock;
        this.currentIndex = 0;
        this.currentBucketStart = clock.instant();
    }

    public void recordWrite() {
        buckets[currentIndex].incrementAndGet();
    }

    public long getCount() {
        long sum = 0;
        for (AtomicLong bucket : buckets) {
            sum += bucket.get();
        }
        return sum;
    }

    public boolean tryRotate() {
        Instant now = clock.instant();
        long elapsedMillis = now.toEpochMilli() - currentBucketStart.toEpochMilli();
        if (elapsedMillis < bucketDurationMillis) {
            return false;
        }
        int steps = (int) (elapsedMillis / bucketDurationMillis);
        if (steps >= buckets.length) {
            for (AtomicLong bucket : buckets) {
                bucket.set(0);
            }
            currentIndex = 0;
        } else {
            for (int i = 0; i < steps; i++) {
                currentIndex = (currentIndex + 1) % buckets.length;
                buckets[currentIndex].set(0);
            }
        }
        currentBucketStart = now;
        return true;
    }

    public boolean isEmpty() {
        for (AtomicLong bucket : buckets) {
            if (bucket.get() != 0) {
                return false;
            }
        }
        return true;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
