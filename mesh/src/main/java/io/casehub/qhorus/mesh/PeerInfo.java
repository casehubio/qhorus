package io.casehub.qhorus.mesh;

import java.util.Map;

public record PeerInfo(String instanceId, String description, Map<String, String> metadata) {}
