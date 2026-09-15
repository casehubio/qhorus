package io.casehub.qhorus.api.message;

public record WaitResult(
    boolean found,
    boolean timedOut,
    String correlationId,
    Message message,
    String status
) {}
