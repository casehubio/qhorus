package io.casehub.qhorus.api.gateway;

import io.casehub.qhorus.api.message.MessageType;

public record NormalisedMessage(
        MessageType type,
        String content,
        String senderInstanceId,
        String correlationId,
        Long inReplyTo,
        java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs,
        String target) {}
