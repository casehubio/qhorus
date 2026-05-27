package io.casehub.qhorus.runtime.watchdog;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.casehub.qhorus.api.WatchdogEnabledProfile;
import io.casehub.qhorus.api.watchdog.BarrierStuckContext;
import io.casehub.qhorus.api.watchdog.WatchdogConditionType;
import io.casehub.qhorus.runtime.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(WatchdogEnabledProfile.class)
class WatchdogAlertEventTest {

    @Inject
    WatchdogEvaluationService service;

    @BeforeEach
    void resetCapture() {
        AlertEventCapture.expectCount(0);
    }

    @Test
    @Transactional
    void barrierStuck_firesWatchdogAlertEvent() throws InterruptedException {
        Channel ch = new Channel();
        ch.id = UUID.randomUUID();
        ch.name = "barrier-test-" + ch.id;
        ch.semantic = ChannelSemantic.BARRIER;
        ch.barrierContributors = "agent-alpha,agent-beta";
        ch.lastActivityAt = Instant.now().minusSeconds(3600);
        ch.persist();

        Watchdog w = new Watchdog();
        w.conditionType = "BARRIER_STUCK";
        w.targetName = ch.name;
        w.thresholdSeconds = 0;
        w.notificationChannel = "alerts-" + UUID.randomUUID();
        w.createdBy = "test";
        w.persist();

        AlertEventCapture.expectCount(1);
        service.evaluateAll();

        assertTrue(AlertEventCapture.await(2, TimeUnit.SECONDS), "WatchdogAlertEvent not delivered within 2s");
        assertThat(AlertEventCapture.events).hasSize(1);

        var event = AlertEventCapture.events.get(0);
        assertThat(event.conditionType()).isEqualTo(WatchdogConditionType.BARRIER_STUCK);
        assertThat(event.watchdogId()).isEqualTo(w.id);
        assertThat(event.targetName()).isEqualTo(ch.name);

        BarrierStuckContext ctx = (BarrierStuckContext) event.context();
        assertThat(ctx.channelId()).isEqualTo(ch.id);
        assertThat(ctx.channelName()).isEqualTo(ch.name);
        assertThat(ctx.missingContributors()).containsExactlyInAnyOrder("agent-alpha", "agent-beta");
        assertThat(ctx.elapsedSeconds()).isGreaterThan(3500L);
    }
}
