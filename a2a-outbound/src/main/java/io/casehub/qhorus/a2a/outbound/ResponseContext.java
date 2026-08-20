package io.casehub.qhorus.a2a.outbound;

import java.util.UUID;

public record ResponseContext(
        UUID channelId,
        String externalAgentInstanceId,
        String correlationId,
        Long inReplyTo) {}
