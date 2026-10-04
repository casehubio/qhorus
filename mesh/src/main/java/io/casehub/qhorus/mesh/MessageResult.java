package io.casehub.qhorus.mesh;

import io.casehub.qhorus.api.message.MessageType;

public record MessageResult(Long messageId, String channel, MessageType type) {}
