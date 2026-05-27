package io.casehub.qhorus.runtime.watchdog;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.casehub.qhorus.api.WatchdogAlertEndpointsProfile;
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
@TestProfile(WatchdogAlertEndpointsProfile.class)
class WatchdogAlertE2ETest {

    @Inject
    WatchdogEvaluationService service;

    @BeforeEach
    void reset() {
        TestSlackConnectorE2E.clear();
        AlertEventCapture.expectCount(1);
    }

    @Test
    @Transactional
    void barrierStuck_eventFlowsToConnector() throws InterruptedException {
        Channel ch = new Channel();
        ch.id = UUID.randomUUID();
        ch.name = "e2e-barrier-" + ch.id;
        ch.semantic = ChannelSemantic.BARRIER;
        ch.barrierContributors = "agent-x";
        ch.lastActivityAt = Instant.now().minusSeconds(3600);
        ch.persist();

        Watchdog w = new Watchdog();
        w.conditionType = "BARRIER_STUCK";
        w.targetName = ch.name;
        w.thresholdSeconds = 0;
        w.notificationChannel = "e2e-alerts-" + UUID.randomUUID();
        w.createdBy = "test";
        w.persist();

        service.evaluateAll();

        assertTrue(AlertEventCapture.await(2, TimeUnit.SECONDS),
                "WatchdogAlertEvent not delivered within 2s");

        assertThat(AlertEventCapture.events.get(0).conditionType())
                .isEqualTo(WatchdogConditionType.BARRIER_STUCK);

        assertThat(TestSlackConnectorE2E.sent).hasSize(1);
        var msg = TestSlackConnectorE2E.sent.get(0);
        assertThat(msg.destination()).isEqualTo("https://hooks.slack.com/test");
        assertThat(msg.title()).startsWith("[Qhorus Alert] BARRIER_STUCK:");
        assertThat(msg.body()).contains("agent-x");
    }
}
