package io.casehub.qhorus.api.message;

public record DeleteMessageResult(
    Long messageId,
    boolean deleted,
    String sender,
    String messageType,
    String preview,
    String status
) {}
