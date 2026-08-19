package io.casehub.a2a.model;

public record A2ATaskStatus(
    A2ATaskState state,
    A2AMessage message
) {}
