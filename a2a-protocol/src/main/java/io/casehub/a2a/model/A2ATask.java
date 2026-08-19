package io.casehub.a2a.model;

import java.util.List;

public record A2ATask(
    String id,
    String contextId,
    A2ATaskStatus status,
    List<A2AArtifact> artifacts,
    List<A2AMessage> history
) {}
