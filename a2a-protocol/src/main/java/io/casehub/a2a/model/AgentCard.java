package io.casehub.a2a.model;

import java.util.List;
import java.util.Map;

public record AgentCard(
    String name,
    String description,
    String url,
    String version,
    List<AgentSkill> skills,
    AgentCapabilities capabilities,
    Map<String, Object> authentication,
    String tenancyId,
    List<AgentRef> agents
) {
    public record AgentRef(String name, String url) {}

    public boolean hasSkill(String skillId) {
        return skills != null && skills.stream().anyMatch(s -> skillId.equals(s.id()));
    }
}
