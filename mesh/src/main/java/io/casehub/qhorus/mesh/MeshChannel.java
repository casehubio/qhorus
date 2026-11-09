package io.casehub.qhorus.mesh;

import java.util.Map;
import java.util.UUID;

public record MeshChannel(UUID id, String name, Map<String, String> metadata) {
}
