package io.casehub.qhorus.notification.bridge;

import io.casehub.platform.api.subscription.SubscribableEvent;

import java.util.Objects;
import java.util.UUID;

public record QhorusObligationEvent(
        Kind kind,
        String tenancyId,
        String obligor,
        String requester,
        UUID channelId,
        String channelName,
        String senderId,
        String correlationId,
        String content
) implements SubscribableEvent {

    public enum Kind {
        ASSIGNED, FULFILLED, FAILED, DECLINED, EXPIRED
    }

    private static final String TYPE_PREFIX = "io.casehub.qhorus.obligation.";

    public QhorusObligationEvent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(tenancyId, "tenancyId");
    }

    @Override
    public String type() {
        return TYPE_PREFIX + kind.name().toLowerCase();
    }
}
