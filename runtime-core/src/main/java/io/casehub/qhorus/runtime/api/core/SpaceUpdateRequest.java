package io.casehub.qhorus.runtime.api.core;

import java.util.UUID;

public record SpaceUpdateRequest(String name, String description, UUID parentSpaceId) {
}
