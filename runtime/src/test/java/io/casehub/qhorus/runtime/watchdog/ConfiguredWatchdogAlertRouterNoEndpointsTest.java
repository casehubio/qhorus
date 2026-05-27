package io.casehub.qhorus.runtime.watchdog;

import io.casehub.qhorus.api.WatchdogEnabledProfile;
import io.casehub.qhorus.api.watchdog.AgentStaleContext;
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
@TestProfile(WatchdogEnabledProfile.class)
class ConfiguredWatchdogAlertRouterNoEndpointsTest {

    @Inject
    WatchdogAlertRouter router;

    @Test
    void routeReturnsEmptyListWhenNoEndpointsConfigured() {
        WatchdogAlertEvent event = new WatchdogAlertEvent(
                UUID.randomUUID(), "*", "alerts", "summary",
                Instant.now(), new AgentStaleContext(1L, List.of()));

        assertThat(router.route(event)).isEmpty();
    }
}
