package io.casehub.qhorus.cluster;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.ArtefactRef;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InternalDispatchRequest(
        UUID channelId, String sender, String type, String content,
        String payload, String correlationId, Long inReplyTo,
        List<ArtefactRef> artefactRefs, String target,
        String subjectId, String causedByEntryId, String actorType,
        String deadline, String telemetry, String tenancyId, String topic,
        String correctsMessageId, boolean retraction) {

    public static InternalDispatchRequest from(MessageDispatch d) {
        return new InternalDispatchRequest(
                d.channelId(), d.sender(),
                d.type() != null ? d.type().name() : null,
                d.content(), d.payload(), d.correlationId(), d.inReplyTo(),
                d.artefactRefs(), d.target(),
                d.subjectId() != null ? d.subjectId().toString() : null,
                d.causedByEntryId() != null ? d.causedByEntryId().toString() : null,
                d.actorType() != null ? d.actorType().name() : null,
                d.deadline() != null ? d.deadline().toString() : null,
                d.telemetry(), d.tenancyId(), d.topic(),
                d.correctsMessageId() != null ? d.correctsMessageId().toString() : null,
                d.retraction());
    }

    public MessageDispatch toMessageDispatch() {
        return new MessageDispatch(
                channelId, sender,
                type != null ? MessageType.valueOf(type) : null,
                content, payload, correlationId, inReplyTo,
                artefactRefs, target,
                subjectId != null ? UUID.fromString(subjectId) : null,
                causedByEntryId != null ? UUID.fromString(causedByEntryId) : null,
                actorType != null ? ActorType.valueOf(actorType) : null,
                deadline != null ? Instant.parse(deadline) : null,
                telemetry, tenancyId, topic,
                correctsMessageId != null ? Long.parseLong(correctsMessageId) : null,
                retraction);
    }
}
