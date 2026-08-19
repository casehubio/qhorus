package io.casehub.qhorus.api.instance;

import java.time.Instant;
import java.util.UUID;

public record ExternalAgentBinding(
    UUID id,
    String instanceId,
    String endpoint,
    String authConfigKey,
    String protocolVersion,
    Instant createdAt
) {}
