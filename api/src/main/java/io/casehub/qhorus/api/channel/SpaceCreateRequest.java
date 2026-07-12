package io.casehub.qhorus.api.channel;

import java.util.UUID;

public record SpaceCreateRequest(
        String name,
        String description,
        UUID parentSpaceId
) {
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
    }
}
