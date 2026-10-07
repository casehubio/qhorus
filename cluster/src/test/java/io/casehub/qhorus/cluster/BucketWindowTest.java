package io.casehub.qhorus.cluster;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class BucketWindowTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");

    private Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneId.of("UTC"));
    }

    @Test
    void recordWriteIncrementsCurrent() {
        var window = new BucketWindow(10, Duration.ofSeconds(30), fixedClock(NOW));
        window.recordWrite();
        window.recordWrite();
        assertThat(window.getCount()).isEqualTo(2);
    }

    @Test
    void getCountSumsAllBuckets() {
        var window = new BucketWindow(3, Duration.ofSeconds(10), fixedClock(NOW));
        window.recordWrite();
        window.recordWrite();
        assertThat(window.getCount()).isEqualTo(2);
    }

    @Test
    void tryRotateAdvancesWhenDurationElapsed() {
        var window = new BucketWindow(3, Duration.ofSeconds(10), fixedClock(NOW));
        window.recordWrite(); // bucket 0: 1

        window.setClock(fixedClock(NOW.plusSeconds(11)));
        assertThat(window.tryRotate()).isTrue();

        window.recordWrite(); // bucket 1: 1
        assertThat(window.getCount()).isEqualTo(2);
    }

    @Test
    void tryRotateDoesNotAdvanceBeforeDuration() {
        var window = new BucketWindow(3, Duration.ofSeconds(10), fixedClock(NOW));
        window.recordWrite();
        window.setClock(fixedClock(NOW.plusSeconds(5)));
        assertThat(window.tryRotate()).isFalse();
        assertThat(window.getCount()).isEqualTo(1);
    }

    @Test
    void rotationClearsOldestBucket() {
        var window = new BucketWindow(3, Duration.ofSeconds(10), fixedClock(NOW));
        window.recordWrite(); // bucket 0: 1

        window.setClock(fixedClock(NOW.plusSeconds(11)));
        window.tryRotate(); // now on bucket 1
        window.recordWrite(); // bucket 1: 1

        window.setClock(fixedClock(NOW.plusSeconds(22)));
        window.tryRotate(); // now on bucket 2

        window.setClock(fixedClock(NOW.plusSeconds(33)));
        window.tryRotate(); // now on bucket 0 — clears old bucket 0 data

        assertThat(window.getCount()).isEqualTo(1); // only bucket 1 survives
    }

    @Test
    void isEmptyWhenAllBucketsZero() {
        var window = new BucketWindow(3, Duration.ofSeconds(10), fixedClock(NOW));
        assertThat(window.isEmpty()).isTrue();

        window.recordWrite();
        assertThat(window.isEmpty()).isFalse();
    }

    @Test
    void multipleRotationsOnLargeTimeJump() {
        var window = new BucketWindow(3, Duration.ofSeconds(10), fixedClock(NOW));
        window.recordWrite(); // bucket 0: 1

        // jump 40s — should rotate through all buckets and clear everything
        window.setClock(fixedClock(NOW.plusSeconds(40)));
        window.tryRotate();
        assertThat(window.isEmpty()).isTrue();
    }
}
