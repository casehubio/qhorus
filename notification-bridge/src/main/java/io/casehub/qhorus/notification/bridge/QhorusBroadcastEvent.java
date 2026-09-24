package io.casehub.qhorus.notification.bridge;

import io.casehub.platform.api.subscription.SubscribableEvent;

import java.util.Objects;
import java.util.UUID;

public record QhorusBroadcastEvent(
        String tenancyId,
        String recipientId,
        String capabilityTag,
        UUID channelId,
        String channelName,
        String senderId,
        String messageType,
        String content
) implements SubscribableEvent {

    private static final String TYPE_PREFIX = "io.casehub.qhorus.broadcast.";

    public QhorusBroadcastEvent {
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(capabilityTag, "capabilityTag");
    }

    @Override
    public String type() {
        return TYPE_PREFIX + capabilityTag;
    }
}
