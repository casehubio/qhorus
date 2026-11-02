package io.casehub.qhorus.api.spi;

import java.util.Set;

public record ResolvedIdentity(
    String actorId,
    String tenancyId,
    Set<String> roles
) {
    public ResolvedIdentity {
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId required");
        if (tenancyId == null || tenancyId.isBlank()) throw new IllegalArgumentException("tenancyId required");
        if (roles == null) roles = Set.of();
    }
}
