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

    /**
     * V1 fan-out: delivers every alert to all configured endpoints regardless of which
     * watchdog condition fired or what {@link io.casehub.qhorus.api.watchdog.AlertContext}
     * it carries. The {@code event} parameter is intentionally unused.
     *
     * <p>To route selectively (e.g. only AGENT_STALE alerts go to Slack), provide an
     * {@link io.casehub.qhorus.api.watchdog.WatchdogAlertRouter} implementation with
     * any normal CDI scope (e.g. {@code @ApplicationScoped}). CDI automatically selects
     * any non-{@code @DefaultBean} qualifying bean over this default — no
     * {@code @Alternative} or priority annotation needed.
     */
    @Override
    public List<AlertDeliveryTarget> route(WatchdogAlertEvent event) {
        return config.watchdog().alert().endpoints().stream()
                .map(ep -> new AlertDeliveryTarget(ep.connectorId(), ep.destination()))
                .toList();
    }
}
