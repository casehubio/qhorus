package io.casehub.qhorus.api.message;

import java.time.Instant;
import java.util.UUID;

public record DispatchMessageRequest(
    UUID channelId,
    String type,
    String content,
    String correlationId,
    Long inReplyTo,
    String target,
    String topic,
    Instant deadline
) {}
