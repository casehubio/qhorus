package io.casehub.qhorus.cluster;

import io.casehub.qhorus.api.message.MessageType;

import java.util.UUID;

public record ProxyFallbackEvent(
        UUID channelId,
        String ownerNodeId,
        String localNodeId,
        String sender,
        MessageType messageType,
        String errorMessage) {
}
