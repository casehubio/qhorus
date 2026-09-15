package io.casehub.qhorus.api.message;

public record CancelWaitResult(
    String correlationId,
    boolean cancelled,
    String message
) {}
