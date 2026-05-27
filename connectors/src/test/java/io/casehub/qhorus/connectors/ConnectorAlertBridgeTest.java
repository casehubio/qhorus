package io.casehub.qhorus.connectors;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.casehub.qhorus.api.watchdog.AgentStaleContext;
import io.casehub.qhorus.api.watchdog.AlertDeliveryTarget;
import io.casehub.qhorus.api.watchdog.WatchdogAlertEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ConnectorAlertBridgeTest {

    @Inject
    ConnectorAlertBridge bridge;

    @BeforeEach
    void reset() {
        TestSlackConnector.clear();
        TestWatchdogAlertRouter.targets = List.of(
                new AlertDeliveryTarget("slack", "https://hooks.slack.com/test"));
    }

    @Test
    void onAlert_sendsToConfiguredConnector() {
        WatchdogAlertEvent event = new WatchdogAlertEvent(
                UUID.randomUUID(), "prod-instances", "alerts",
                "AGENT_STALE: 2 stale agent(s) detected",
                Instant.now(),
                new AgentStaleContext(2L, List.of("id-a", "id-b")));

        bridge.onAlert(event);

        assertThat(TestSlackConnector.sent).hasSize(1);
        var msg = TestSlackConnector.sent.get(0);
        assertThat(msg.destination()).isEqualTo("https://hooks.slack.com/test");
        assertThat(msg.title()).isEqualTo("[Qhorus Alert] AGENT_STALE: prod-instances");
        assertThat(msg.body()).contains("AGENT_STALE: 2 stale agent(s) detected");
        assertThat(msg.body()).contains("id-a");
        assertThat(msg.body()).contains("id-b");
    }

    @Test
    void onAlert_unknownConnectorId_logsAndContinues() {
        TestWatchdogAlertRouter.targets = List.of(
                new AlertDeliveryTarget("does-not-exist", "https://example.com"),
                new AlertDeliveryTarget("slack", "https://hooks.slack.com/test"));

        WatchdogAlertEvent event = new WatchdogAlertEvent(
                UUID.randomUUID(), "*", "alerts", "AGENT_STALE: 1 stale agent",
                Instant.now(), new AgentStaleContext(1L, List.of("id-x")));

        bridge.onAlert(event);

        assertThat(TestSlackConnector.sent).hasSize(1);
    }

    @Test
    void onAlert_noTargets_doesNothing() {
        TestWatchdogAlertRouter.targets = List.of();

        WatchdogAlertEvent event = new WatchdogAlertEvent(
                UUID.randomUUID(), "*", "alerts", "AGENT_STALE: 1",
                Instant.now(), new AgentStaleContext(1L, List.of()));

        bridge.onAlert(event);

        assertThat(TestSlackConnector.sent).isEmpty();
    }
}
