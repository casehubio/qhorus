package io.casehub.qhorus.graphql.dto;

import io.casehub.qhorus.api.message.DispatchResult;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Type;

@Type("DispatchResult")
public record DispatchResultType(
        Long messageId,
        UUID channelId,
        String sender,
        String messageType,
        String correlationId,
        Long inReplyTo,
        List<String> advisories) {

    public static DispatchResultType from(DispatchResult result) {
        return new DispatchResultType(
                result.messageId(),
                result.channelId(),
                result.sender(),
                result.type() != null ? result.type().name() : null,
                result.correlationId(),
                result.inReplyTo(),
                result.advisories());
    }
}
