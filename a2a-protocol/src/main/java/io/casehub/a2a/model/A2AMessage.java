package io.casehub.a2a.model;

import java.util.List;
import java.util.Map;

public record A2AMessage(
    String role,
    List<A2APart> parts,
    String messageId,
    String taskId,
    String contextId,
    Map<String, Object> metadata
) {}
