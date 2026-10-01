package io.casehub.qhorus.notification.bridge.core;

import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.subscription.NotificationTarget;
import io.casehub.platform.api.subscription.NotificationTemplate;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionScope;
import io.casehub.platform.api.subscription.SubscriptionStore;
import io.casehub.platform.api.subscription.TargetType;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.casehub.platform.api.identity.TenancyConstants.PLATFORM_TENANT_ID;

public class QhorusSubscriptionBootstrapCore {

    private static final String OWNER_ID = "system:qhorus";
    private static final String TYPE_PREFIX = "io.casehub.qhorus.obligation.";
    private static final String BROADCAST_TYPE_PREFIX = "io.casehub.qhorus.broadcast.";

    private final SubscriptionStore subscriptionStore;

    public QhorusSubscriptionBootstrapCore(SubscriptionStore subscriptionStore) {
        this.subscriptionStore = subscriptionStore;
    }

    public void bootstrap() {
        Set<String> existing = subscriptionStore.findAllEnabled()
                .filter(s -> s.eventType().startsWith(TYPE_PREFIX) || s.eventType().startsWith(BROADCAST_TYPE_PREFIX))
                .map(Subscription::eventType)
                .collect(Collectors.toSet());

        register(existing, "assigned", "obligor",
                 "Obligation assigned in {channelName}", NotificationSeverity.INFO);
        register(existing, "proposed", "obligor",
                 "Proposal received in {channelName}", NotificationSeverity.INFO);
        register(existing, "fulfilled", "requester",
                 "Request completed in {channelName}", NotificationSeverity.INFO);
        register(existing, "failed", "requester",
                 "Request failed in {channelName}", NotificationSeverity.WARNING);
        register(existing, "declined", "requester",
                 "Request declined in {channelName}", NotificationSeverity.WARNING);
        register(existing, "expired", "requester",
                 "Request expired in {channelName}", NotificationSeverity.URGENT);

        registerBroadcast(existing);
    }

    private void register(Set<String> existing, String kind, String targetField,
                          String titlePattern, NotificationSeverity severity) {
        String eventType = TYPE_PREFIX + kind;
        if (existing.contains(eventType)) {
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
        } catch (Exception ignored) {
        }
    }

    private void registerBroadcast(Set<String> existing) {
        if (existing.stream().anyMatch(t -> t.startsWith(BROADCAST_TYPE_PREFIX))) {
            return;
        }
        try {
            subscriptionStore.store(new SubscriptionInput(
                    OWNER_ID,
                    PLATFORM_TENANT_ID,
                    "qhorus.broadcast",
                BROADCAST_TYPE_PREFIX + "*",
                List.of(),
                List.of(new NotificationTarget(TargetType.EVENT_FIELD, "recipientId")),
                false,
                new NotificationTemplate(
                        "Broadcast from {senderId} on {channelName}",
                        "{content}",
                        NotificationSeverity.INFO,
                        "qhorus.broadcast",
                        null,
                        "channel",
                        "channelId",
                        "senderId"),
                true,
                    SubscriptionScope.SYSTEM));
        } catch (Exception ignored) {
        }
    }
}
