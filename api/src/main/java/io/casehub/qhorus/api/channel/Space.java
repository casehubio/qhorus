package io.casehub.qhorus.api.channel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Space(
        UUID id,
        String name,
        String description,
        UUID parentSpaceId,
        String tenancyId,
        Instant createdAt,
        List<String> defaultProtocols,
        List<String> defaultProtocolParticipants) {

    public Space {
        if (name == null || name.isBlank()) {throw new IllegalArgumentException("name is required");}
        defaultProtocols = defaultProtocols != null ? List.copyOf(defaultProtocols) : List.of();
        defaultProtocolParticipants = defaultProtocolParticipants != null ? List.copyOf(defaultProtocolParticipants) : List.of();
    }

    public Space(UUID id, String name, String description, UUID parentSpaceId,
                 String tenancyId, Instant createdAt) {
        this(id, name, description, parentSpaceId, tenancyId, createdAt, List.of(), List.of());
    }
}
