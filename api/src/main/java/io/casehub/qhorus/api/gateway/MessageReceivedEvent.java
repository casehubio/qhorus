package io.casehub.qhorus.api.gateway;

import java.util.UUID;

import io.casehub.qhorus.api.message.MessageType;

/**
 * Fired by {@link MessageObserver} implementations after every message persisted
 * to a qhorus channel — all 9 speech-act types.
 *
 * <p>{@code content} is always {@code null} for {@link MessageType#EVENT}.
 * EVENT telemetry is stored in dedicated ledger columns, not in content.
 * See PP-20260508-90428f.
 */
public record MessageReceivedEvent(
        String channelName,
        UUID channelId,
        MessageType messageType,
        String senderId,
        String correlationId,
        String content) {

    public MessageReceivedEvent {
        if (messageType == MessageType.EVENT && content != null) {
            throw new IllegalArgumentException(
                    "EVENT messages must have null content per PP-20260508-90428f");
        }
    }
}
