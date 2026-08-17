package io.casehub.qhorus.graphql.dto;

import io.casehub.qhorus.api.message.Message;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Type;

@Type("QhorusMessage")
public record MessageType(
        Long id,
        UUID channelId,
        String sender,
        String messageType,
        String actorType,
        String content,
        String correlationId,
        Long inReplyTo,
        int replyCount,
        String target,
        String topic,
        UUID commitmentId,
        Instant createdAt) {

    public static MessageType from(Message message) {
        return new MessageType(
                message.id(),
                message.channelId(),
                message.sender(),
                message.messageType() != null ? message.messageType().name() : null,
                message.actorType() != null ? message.actorType().name() : null,
                message.content(),
                message.correlationId(),
                message.inReplyTo(),
                message.replyCount(),
                message.target(),
                message.topic(),
                message.commitmentId(),
                message.createdAt());
    }
}
