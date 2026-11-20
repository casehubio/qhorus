package io.casehub.qhorus.playbook.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProtocolDeadlineSchedulerTest {

    private ProtocolDeadlineScheduler scheduler;
    private List<DeadlineKey> firedKeys;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-11-20T12:00:00Z");
        firedKeys = new ArrayList<>();
        scheduler = new ProtocolDeadlineScheduler(firedKeys::add);
        scheduler.clock = Clock.fixed(now, ZoneOffset.UTC);
    }

    private DeadlineKey key(UUID channelId) {
        return new DeadlineKey(channelId, "gate", "response-timeout");
    }

    @Test
    void sweep_firesEvent_forPastDeadline() {
        UUID ch = UUID.randomUUID();
        scheduler.register(key(ch), now.minus(Duration.ofMinutes(1)));

        scheduler.sweep();

        assertThat(firedKeys).hasSize(1);
        assertThat(firedKeys.get(0).channelId()).isEqualTo(ch);
        assertThat(scheduler.activeCount()).isZero();
    }

    @Test
    void sweep_firesEvent_forExactNowDeadline() {
        UUID ch = UUID.randomUUID();
        scheduler.register(key(ch), now);

        scheduler.sweep();

        assertThat(firedKeys).hasSize(1);
        assertThat(scheduler.activeCount()).isZero();
    }

    @Test
    void sweep_doesNotFire_forFutureDeadline() {
        UUID ch = UUID.randomUUID();
        scheduler.register(key(ch), now.plus(Duration.ofMinutes(5)));

        scheduler.sweep();

        assertThat(firedKeys).isEmpty();
        assertThat(scheduler.activeCount()).isEqualTo(1);
    }

    @Test
    void cancel_removesDeadline() {
        UUID ch = UUID.randomUUID();
        DeadlineKey dk = key(ch);
        scheduler.register(dk, now.plus(Duration.ofMinutes(1)));

        scheduler.cancel(dk);

        assertThat(scheduler.activeCount()).isZero();
    }

    @Test
    void clear_removesByChannelAndProtocol() {
        UUID ch = UUID.randomUUID();
        scheduler.register(new DeadlineKey(ch, "gate", "timeout-a"),
                now.plus(Duration.ofMinutes(1)));
        scheduler.register(new DeadlineKey(ch, "gate", "timeout-b"),
                now.plus(Duration.ofMinutes(2)));
        scheduler.register(new DeadlineKey(ch, "other-gate", "timeout-c"),
                now.plus(Duration.ofMinutes(3)));

        scheduler.clear(ch, "gate");

        assertThat(scheduler.activeCount()).isEqualTo(1);
    }

    @Test
    void clearAll_removesByChannel() {
        UUID ch = UUID.randomUUID();
        scheduler.register(new DeadlineKey(ch, "gate-a", "t1"),
                now.plus(Duration.ofMinutes(1)));
        scheduler.register(new DeadlineKey(ch, "gate-b", "t2"),
                now.plus(Duration.ofMinutes(2)));
        scheduler.register(new DeadlineKey(UUID.randomUUID(), "gate-a", "t3"),
                now.plus(Duration.ofMinutes(3)));

        scheduler.clearAll(ch);

        assertThat(scheduler.activeCount()).isEqualTo(1);
    }

    @Test
    void sweep_removesAtomically_andFiresMultiple() {
        UUID ch1 = UUID.randomUUID();
        UUID ch2 = UUID.randomUUID();
        scheduler.register(key(ch1), now.minus(Duration.ofSeconds(10)));
        scheduler.register(key(ch2), now.minus(Duration.ofSeconds(5)));

        scheduler.sweep();

        assertThat(firedKeys).hasSize(2);
        assertThat(scheduler.activeCount()).isZero();
    }

    @Test
    void sweep_continuesOnFireFailure() {
        List<DeadlineKey> tracking = new ArrayList<>();
        int[] callCount = {0};
        ProtocolDeadlineScheduler failingScheduler = new ProtocolDeadlineScheduler(key -> {
            callCount[0]++;
            tracking.add(key);
            if (callCount[0] == 1) throw new RuntimeException("fire failed");
        });
        failingScheduler.clock = Clock.fixed(now, ZoneOffset.UTC);

        failingScheduler.register(new DeadlineKey(UUID.randomUUID(), "gate", "t1"),
                now.minus(Duration.ofSeconds(10)));
        failingScheduler.register(new DeadlineKey(UUID.randomUUID(), "gate", "t2"),
                now.minus(Duration.ofSeconds(5)));

        failingScheduler.sweep();

        assertThat(tracking).hasSize(2);
        assertThat(failingScheduler.activeCount()).isZero();
    }
}
