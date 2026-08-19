package io.casehub.a2a.model;

public record AgentCapabilities(
    boolean streaming,
    boolean pushNotifications
) {}
