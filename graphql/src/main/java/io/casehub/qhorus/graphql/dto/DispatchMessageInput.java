package io.casehub.qhorus.graphql.dto;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Input;

@Input("DispatchMessageInput")
public record DispatchMessageInput(
        UUID channelId,
        String type,
        String content,
        String correlationId,
        Long inReplyTo,
        String target,
        String topic,
        Instant deadline) {}
