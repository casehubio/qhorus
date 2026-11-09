package io.casehub.qhorus.mesh;

import java.util.List;

public record MeshMessagePage(String channel, List<MeshMessage> messages) {
}
