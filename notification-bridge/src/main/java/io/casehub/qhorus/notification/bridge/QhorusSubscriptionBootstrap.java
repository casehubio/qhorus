package io.casehub.qhorus.notification.bridge;

import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.subscription.NotificationTarget;
import io.casehub.platform.api.subscription.NotificationTemplate;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionScope;
import io.casehub.platform.api.subscription.SubscriptionStore;
import io.casehub.platform.api.subscription.TargetType;

import io.quarkus.runtime.StartupEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.casehub.platform.api.identity.TenancyConstants.PLATFORM_TENANT_ID;

@ApplicationScoped
public class QhorusSubscriptionBootstrap {

    private static final Logger LOG = Logger.getLogger(QhorusSubscriptionBootstrap.class);
    private static final String OWNER_ID = "system:qhorus";
    private static final String TYPE_PREFIX = "io.casehub.qhorus.obligation.";

    private final SubscriptionStore subscriptionStore;

    @Inject
    public QhorusSubscriptionBootstrap(SubscriptionStore subscriptionStore) {
        this.subscriptionStore = subscriptionStore;
    }

    void onStartup(@Observes StartupEvent event) {
        Set<String> existing = subscriptionStore.findAllEnabled()
                .filter(s -> s.eventType().startsWith(TYPE_PREFIX))
                .map(Subscription::eventType)
                .collect(Collectors.toSet());

        register(existing, "assigned", "obligor",
                "Obligation assigned in {channelName}", NotificationSeverity.INFO);
        register(existing, "fulfilled", "requester",
                "Request completed in {channelName}", NotificationSeverity.INFO);
        register(existing, "failed", "requester",
                "Request failed in {channelName}", NotificationSeverity.WARNING);
        register(existing, "declined", "requester",
                "Request declined in {channelName}", NotificationSeverity.WARNING);
        register(existing, "expired", "requester",
                "Request expired in {channelName}", NotificationSeverity.URGENT);
    }

    private void register(Set<String> existing, String kind, String targetField,
                          String titlePattern, NotificationSeverity severity) {
        String eventType = TYPE_PREFIX + kind;
        if (existing.contains(eventType)) {
            LOG.debugf("Subscription for %s already exists — skipping", eventType);
            return;
        }
        try {
            subscriptionStore.store(new SubscriptionInput(
                    OWNER_ID,
                    PLATFORM_TENANT_ID,
                    "qhorus.obligation." + kind,
                    eventType,
                    List.of(),
                    List.of(new NotificationTarget(TargetType.EVENT_FIELD, targetField)),
                    false,
                    new NotificationTemplate(
                            titlePattern,
                            "{content}",
                            severity,
                            "qhorus.obligation." + kind,
                            null,
                            "channel",
                            "channelId",
                            "senderId"),
                    true,
                    SubscriptionScope.SYSTEM));
            LOG.infof("Registered default subscription for %s", eventType);
        } catch (Exception e) {
            LOG.warnf("Failed to register default subscription for %s: %s", eventType, e.getMessage());
        }
    }
}
