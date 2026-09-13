package io.casehub.qhorus.runtime.watchdog;

import java.util.List;

import io.casehub.qhorus.api.watchdog.AlertDeliveryTarget;
import io.casehub.qhorus.api.watchdog.WatchdogAlertEvent;
import io.casehub.qhorus.api.watchdog.WatchdogAlertRouter;

public class ConfiguredWatchdogAlertRouter implements WatchdogAlertRouter {

    private final List<AlertDeliveryTarget> targets;

    public ConfiguredWatchdogAlertRouter(List<AlertDeliveryTarget> targets) {
        this.targets = targets;
    }

    @Override
    public List<AlertDeliveryTarget> route(WatchdogAlertEvent event) {
        return targets;
    }
}
