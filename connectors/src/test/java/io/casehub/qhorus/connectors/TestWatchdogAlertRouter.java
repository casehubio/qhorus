package io.casehub.qhorus.connectors;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.qhorus.api.watchdog.AlertDeliveryTarget;
import io.casehub.qhorus.api.watchdog.WatchdogAlertEvent;
import io.casehub.qhorus.api.watchdog.WatchdogAlertRouter;
import io.quarkus.test.Mock;

@Mock
@ApplicationScoped
public class TestWatchdogAlertRouter implements WatchdogAlertRouter {

    public static volatile List<AlertDeliveryTarget> targets = List.of(
            new AlertDeliveryTarget("slack", "https://hooks.slack.com/test"));

    @Override
    public List<AlertDeliveryTarget> route(WatchdogAlertEvent event) {
        return targets;
    }
}
