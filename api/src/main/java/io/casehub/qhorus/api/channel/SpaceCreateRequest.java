package io.casehub.qhorus.api.channel;

import java.util.List;
import java.util.UUID;

public record SpaceCreateRequest(
        String name,
        String description,
        UUID parentSpaceId,
        List<String> defaultProtocols,
        List<String> defaultProtocolParticipants
) {
    public SpaceCreateRequest(String name, String description, UUID parentSpaceId) {
        this(name, description, parentSpaceId, null, null);
    }

    public SpaceCreateRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Space name must not be blank");
        }
        name = name.trim();
        if (name.length() > 200) {
            throw new IllegalArgumentException("Space name exceeds 200 chars: '" + name + "'");
        }
        boolean isUuid;
        try { UUID.fromString(name); isUuid = true; }
        catch (IllegalArgumentException ignored) { isUuid = false; }
        if (isUuid) {
            throw new IllegalArgumentException("Space name must not be UUID-shaped: '" + name + "'");
        }
        if (description != null) description = description.trim();
        defaultProtocols = defaultProtocols != null ? List.copyOf(defaultProtocols) : List.of();
        defaultProtocolParticipants = defaultProtocolParticipants != null ? List.copyOf(defaultProtocolParticipants) : List.of();
    }
}
