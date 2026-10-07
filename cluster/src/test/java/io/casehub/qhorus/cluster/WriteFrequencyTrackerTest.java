package io.casehub.qhorus.cluster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WriteFrequencyTrackerTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private static final Duration BUCKET_DURATION = Duration.ofSeconds(30);
    private static final int BUCKET_COUNT = 10;

    private Clock clock;
    private WriteFrequencyTracker tracker;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        tracker = new WriteFrequencyTracker(BUCKET_COUNT, BUCKET_DURATION, clock);
    }

    @Test
    void recordWriteCreatesWindowOnFirstWrite() {
        UUID ch = UUID.randomUUID();
        tracker.recordWrite(ch);
        assertThat(tracker.getCount(ch)).isEqualTo(1);
        assertThat(tracker.channelCount()).isEqualTo(1);
    }

    @Test
    void getCountReturnsZeroForUnknownChannel() {
        assertThat(tracker.getCount(UUID.randomUUID())).isEqualTo(0);
    }

    @Test
    void tracksMultipleChannelsIndependently() {
        UUID ch1 = UUID.randomUUID();
        UUID ch2 = UUID.randomUUID();
        tracker.recordWrite(ch1);
        tracker.recordWrite(ch1);
        tracker.recordWrite(ch2);
        assertThat(tracker.getCount(ch1)).isEqualTo(2);
        assertThat(tracker.getCount(ch2)).isEqualTo(1);
    }

    @Test
    void getActiveChannelsReturnsOnlyNonZero() {
        UUID ch1 = UUID.randomUUID();
        UUID ch2 = UUID.randomUUID();
        tracker.recordWrite(ch1);
        tracker.recordWrite(ch2);
        assertThat(tracker.getActiveChannels()).containsExactlyInAnyOrder(ch1, ch2);
    }

    @Test
    void rotateAllPrunesEmptyChannels() {
        UUID ch = UUID.randomUUID();
        tracker.recordWrite(ch);

        tracker.setClock(Clock.fixed(NOW.plusSeconds(310), ZoneId.of("UTC")));
        tracker.rotateAll();

        assertThat(tracker.getCount(ch)).isEqualTo(0);
        assertThat(tracker.channelCount()).isEqualTo(0);
        assertThat(tracker.getActiveChannels()).isEmpty();
    }

    @Test
    void rotateAllKeepsActiveChannels() {
        UUID active = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        tracker.recordWrite(active);
        tracker.recordWrite(stale);

        tracker.setClock(Clock.fixed(NOW.plusSeconds(310), ZoneId.of("UTC")));
        tracker.rotateAll();

        tracker.recordWrite(active);

        assertThat(tracker.channelCount()).isEqualTo(1);
        assertThat(tracker.getActiveChannels()).containsExactly(active);
    }
}
