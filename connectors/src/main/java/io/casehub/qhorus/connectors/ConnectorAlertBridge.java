package io.casehub.qhorus.connectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.ConnectorService;
import io.casehub.qhorus.api.watchdog.AgentStaleContext;
import io.casehub.qhorus.api.watchdog.AlertDeliveryTarget;
import io.casehub.qhorus.api.watchdog.ApprovalPendingContext;
import io.casehub.qhorus.api.watchdog.BarrierStuckContext;
import io.casehub.qhorus.api.watchdog.ChannelIdleContext;
import io.casehub.qhorus.api.watchdog.QueueDepthContext;
import io.casehub.qhorus.api.watchdog.WatchdogAlertEvent;
import io.casehub.qhorus.api.watchdog.WatchdogAlertRouter;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ConnectorAlertBridge {

    private static final Logger log = Logger.getLogger(ConnectorAlertBridge.class);

    @Inject
    ConnectorService connectorService;

    @Inject
    WatchdogAlertRouter router;

    void onAlert(@ObservesAsync WatchdogAlertEvent event) {
        String title = "[Qhorus Alert] " + event.conditionType() + ": " + event.targetName();
        String body = buildBody(event);
        for (AlertDeliveryTarget target : router.route(event)) {
            try {
                connectorService.send(target.connectorId(),
                        new ConnectorMessage(target.destination(), title, body));
            } catch (IllegalArgumentException e) {
                log.errorf("Unknown connector '%s' for watchdog alert — available: %s",
                        target.connectorId(), connectorService.ids());
            }
        }
    }

    private String buildBody(WatchdogAlertEvent event) {
        return switch (event.context()) {
            case BarrierStuckContext c ->
                    event.summary()
                    + "\nChannel: " + c.channelName()
                    + "\nMissing: " + String.join(", ", c.missingContributors())
                    + "\nElapsed: " + c.elapsedSeconds() + "s";
            case ApprovalPendingContext c ->
                    event.summary()
                    + "\nPending: " + c.pendingCount()
                    + (c.oldestExpiryAt() != null ? "\nOldest expiry: " + c.oldestExpiryAt() : "");
            case AgentStaleContext c ->
                    event.summary()
                    + "\nStale count: " + c.staleCount()
                    + (c.staleInstanceIds().isEmpty() ? ""
                       : "\nIDs: " + String.join(", ", c.staleInstanceIds()));
            case ChannelIdleContext c ->
                    event.summary()
                    + "\nIdle channels: " + String.join(", ", c.channelNames())
                    + "\nIdle > " + c.thresholdSeconds() + "s";
            case QueueDepthContext c ->
                    event.summary()
                    + "\nChannel: " + c.channelName()
                    + "\nDepth: " + c.messageCount() + " (threshold: " + c.threshold() + ")";
        };
    }
}
