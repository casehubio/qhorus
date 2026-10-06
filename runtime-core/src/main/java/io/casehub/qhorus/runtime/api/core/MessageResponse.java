package io.casehub.qhorus.runtime.api.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.casehub.qhorus.api.message.ArtefactRef;
import io.casehub.qhorus.api.message.Message;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageResponse(
        Long id,
        String sender,
        String type,
        String content,
        String payload,
        String correlationId,
        Long inReplyTo,
        String target,
        String topic,
        List<ArtefactRef> artefactRefs,
        Instant createdAt) {

    public static MessageResponse from(Message msg) {
        return new MessageResponse(
                msg.id(), msg.sender(), msg.messageType().name(),
                msg.content(), msg.payload(), msg.correlationId(),
                msg.inReplyTo(), msg.target(), msg.topic(),
                msg.artefactRefs(), msg.createdAt());
    }
}
