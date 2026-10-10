package io.casehub.qhorus.cluster;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

class BucketWindow {

    private final long[]   buckets;
    private final Duration bucketDuration;
    private       int      headIndex;
    private       Clock    clock;
    private       Instant  lastRotation;

    BucketWindow(int bucketCount, Duration bucketDuration, Clock clock) {
        this.buckets        = new long[bucketCount];
        this.bucketDuration = bucketDuration;
        this.clock          = clock;
        this.lastRotation   = clock.instant();
    }

    void recordWrite() {
        buckets[headIndex]++;
    }

    long getCount() {
        long total = 0;
        for (long b : buckets) {
            total += b;
        }
        return total;
    }

    boolean tryRotate() {
        Instant now       = clock.instant();
        long    elapsed   = Duration.between(lastRotation, now).toMillis();
        long    rotations = elapsed / bucketDuration.toMillis();
        if (rotations <= 0) {return false;}

        for (long i = 0; i < Math.min(rotations, buckets.length); i++) {
            headIndex          = (headIndex + 1) % buckets.length;
            buckets[headIndex] = 0;
        }
        lastRotation = now;
        return true;
    }

    boolean isEmpty() {
        return getCount() == 0;
    }

    void setClock(Clock newClock) {
        this.clock = newClock;
    }
}
