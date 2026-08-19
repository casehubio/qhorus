package io.casehub.a2a.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCardTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void roundTrip_withAllFields() throws Exception {
        AgentCard card = new AgentCard(
            "Qhorus Platform",
            "Multi-agent communication mesh",
            "https://example.com",
            "0.2-SNAPSHOT",
            List.of(new AgentSkill("delegate-task", "Task Delegation", "Delegate work")),
            new AgentCapabilities(true, false),
            Map.of("schemes", List.of("bearer")),
            "default",
            List.of(new AgentCard.AgentRef("agent-1", "/.well-known/agents/agent-1.json")));

        String json = MAPPER.writeValueAsString(card);
        AgentCard parsed = MAPPER.readValue(json, AgentCard.class);

        assertThat(parsed.name()).isEqualTo("Qhorus Platform");
        assertThat(parsed.version()).isEqualTo("0.2-SNAPSHOT");
        assertThat(parsed.skills()).hasSize(1);
        assertThat(parsed.capabilities().streaming()).isTrue();
        assertThat(parsed.capabilities().pushNotifications()).isFalse();
        assertThat(parsed.tenancyId()).isEqualTo("default");
        assertThat(parsed.agents()).hasSize(1);
        assertThat(parsed.agents().get(0).name()).isEqualTo("agent-1");
    }

    @Test
    void hasSkill_existingSkill_returnsTrue() {
        AgentCard card = new AgentCard(
            "Test", null, null, null,
            List.of(new AgentSkill("code-review", "Code Review", "Reviews code")),
            null, null, null, null);
        assertThat(card.hasSkill("code-review")).isTrue();
    }

    @Test
    void hasSkill_missingSkill_returnsFalse() {
        AgentCard card = new AgentCard(
            "Test", null, null, null,
            List.of(new AgentSkill("code-review", "Code Review", "Reviews code")),
            null, null, null, null);
        assertThat(card.hasSkill("nonexistent")).isFalse();
    }

    @Test
    void hasSkill_nullSkills_returnsFalse() {
        AgentCard card = new AgentCard("Test", null, null, null,
            null, null, null, null, null);
        assertThat(card.hasSkill("anything")).isFalse();
    }

    @Test
    void nullAgents_serialises() throws Exception {
        AgentCard card = new AgentCard("Test", "desc", "url", "1.0",
            List.of(), new AgentCapabilities(false, false),
            null, null, null);
        String json = MAPPER.writeValueAsString(card);
        assertThat(json).contains("\"name\":\"Test\"");
        AgentCard parsed = MAPPER.readValue(json, AgentCard.class);
        assertThat(parsed.agents()).isNull();
    }
}
