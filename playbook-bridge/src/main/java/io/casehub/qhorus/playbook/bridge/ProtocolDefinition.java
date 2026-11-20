package io.casehub.qhorus.playbook.bridge;

import java.time.Instant;
import java.util.UUID;

public record ProtocolDefinition(
        UUID id,
        UUID channelId,
        String protocolName,
        String yamlContent,
        String tenancyId,
        Instant createdAt) {}
