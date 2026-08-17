package io.casehub.qhorus.graphql.dto;

import io.casehub.qhorus.api.channel.Presence;
import io.casehub.qhorus.api.channel.PresenceChangedEvent;
import io.casehub.qhorus.api.channel.PresenceStatus;
import java.time.Instant;
import org.eclipse.microprofile.graphql.Type;

@Type("Presence")
public record PresenceType(
        String memberId,
        PresenceStatus status,
        PresenceStatus reportedStatus,
        Instant lastSeenAt,
        String statusMessage) {

    public static PresenceType from(Presence presence) {
        return new PresenceType(
                presence.memberId(),
                presence.status(),
                presence.reportedStatus(),
                presence.lastSeenAt(),
                presence.statusMessage());
    }

    public static PresenceType fromEvent(PresenceChangedEvent event) {
        return new PresenceType(
                event.memberId(),
                event.status(),
                event.status(),
                event.occurredAt(),
                null);
    }
}
