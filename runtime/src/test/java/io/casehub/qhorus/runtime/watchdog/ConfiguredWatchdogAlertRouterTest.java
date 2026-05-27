package io.casehub.qhorus.runtime.watchdog;

import io.casehub.qhorus.api.WatchdogAlertEndpointsProfile;
import io.casehub.qhorus.api.watchdog.AgentStaleContext;
import io.casehub.qhorus.api.watchdog.AlertDeliveryTarget;
import io.casehub.qhorus.api.watchdog.WatchdogAlertEvent;
import io.casehub.qhorus.api.watchdog.WatchdogAlertRouter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(WatchdogAlertEndpointsProfile.class)
class ConfiguredWatchdogAlertRouterTest {

    @Inject
    WatchdogAlertRouter router;

    @Test
    void routeReturnsConfiguredEndpoints() {
        WatchdogAlertEvent event = new WatchdogAlertEvent(
                UUID.randomUUID(), "*", "alerts", "AGENT_STALE: 1 stale agent",
                Instant.now(), new AgentStaleContext(1L, List.of("id-1")));

        List<AlertDeliveryTarget> targets = router.route(event);

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).connectorId()).isEqualTo("slack");
        assertThat(targets.get(0).destination()).isEqualTo("https://hooks.slack.com/test");
    }
}
