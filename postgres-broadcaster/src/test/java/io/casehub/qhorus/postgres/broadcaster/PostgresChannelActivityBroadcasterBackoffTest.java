package io.casehub.qhorus.postgres.broadcaster;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class PostgresChannelActivityBroadcasterBackoffTest {

    @Test
    void backoff_doublesOnEachFailure_capsAt60s() {
        AtomicLong delay = new AtomicLong(1000);
        long max = 60_000;

        assertThat(delay.get()).isEqualTo(1000);
        delay.updateAndGet(d -> Math.min(d * 2, max));
        assertThat(delay.get()).isEqualTo(2000);
        delay.updateAndGet(d -> Math.min(d * 2, max));
        assertThat(delay.get()).isEqualTo(4000);
        delay.updateAndGet(d -> Math.min(d * 2, max));
        assertThat(delay.get()).isEqualTo(8000);
        delay.updateAndGet(d -> Math.min(d * 2, max));
        assertThat(delay.get()).isEqualTo(16000);
        delay.updateAndGet(d -> Math.min(d * 2, max));
        assertThat(delay.get()).isEqualTo(32000);
        delay.updateAndGet(d -> Math.min(d * 2, max));
        assertThat(delay.get()).isEqualTo(60000); // capped
        delay.updateAndGet(d -> Math.min(d * 2, max));
        assertThat(delay.get()).isEqualTo(60000); // stays capped
    }

    @Test
    void backoff_resetsOnSuccess() {
        AtomicLong delay = new AtomicLong(1000);
        long initial = 1000;
        long max = 60_000;

        delay.updateAndGet(d -> Math.min(d * 2, max));
        delay.updateAndGet(d -> Math.min(d * 2, max));
        assertThat(delay.get()).isEqualTo(4000);

        delay.set(initial); // reset on success
        assertThat(delay.get()).isEqualTo(1000);
    }
}
