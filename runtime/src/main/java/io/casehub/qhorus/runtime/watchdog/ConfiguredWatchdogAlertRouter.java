package io.casehub.qhorus.runtime.watchdog;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.qhorus.api.watchdog.AlertDeliveryTarget;
import io.casehub.qhorus.api.watchdog.WatchdogAlertEvent;
import io.casehub.qhorus.api.watchdog.WatchdogAlertRouter;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.quarkus.arc.DefaultBean;

@DefaultBean
@ApplicationScoped
public class ConfiguredWatchdogAlertRouter implements WatchdogAlertRouter {

    @Inject
    QhorusConfig config;

    @Override
    public List<AlertDeliveryTarget> route(WatchdogAlertEvent event) {
        return config.watchdog().alert().endpoints().stream()
                .map(ep -> new AlertDeliveryTarget(ep.connectorId(), ep.destination()))
                .toList();
    }
}
