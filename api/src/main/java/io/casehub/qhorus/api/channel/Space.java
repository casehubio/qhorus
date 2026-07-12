package io.casehub.qhorus.api.channel;

import java.time.Instant;
import java.util.UUID;

public record Space(
        UUID id,
        String name,
        String description,
        UUID parentSpaceId,
        String tenancyId,
        Instant createdAt) {

    public Space {
        if (name == null || name.isBlank()) {throw new IllegalArgumentException("name is required");}
    }
}
