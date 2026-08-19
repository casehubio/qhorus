package io.casehub.a2a.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class A2AMessageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void roundTrip_withParts() throws Exception {
        A2AMessage msg = new A2AMessage(
            "user",
            List.of(
                new A2APart.TextPart("Analyze this"),
                new A2APart.DataPart("application/json",
                    MAPPER.createObjectNode().put("depth", "thorough"))),
            "msg-1", "task-1", "channel-1",
            Map.of("agentId", "claude-session-123"));

        String json = MAPPER.writeValueAsString(msg);
        A2AMessage parsed = MAPPER.readValue(json, A2AMessage.class);

        assertThat(parsed.role()).isEqualTo("user");
        assertThat(parsed.parts()).hasSize(2);
        assertThat(parsed.parts().get(0)).isInstanceOf(A2APart.TextPart.class);
        assertThat(parsed.parts().get(1)).isInstanceOf(A2APart.DataPart.class);
        assertThat(parsed.messageId()).isEqualTo("msg-1");
        assertThat(parsed.metadata()).containsEntry("agentId", "claude-session-123");
    }

    @Test
    void roundTrip_nullMetadata() throws Exception {
        A2AMessage msg = new A2AMessage("agent",
            List.of(new A2APart.TextPart("done")),
            null, null, null, null);
        String json = MAPPER.writeValueAsString(msg);
        A2AMessage parsed = MAPPER.readValue(json, A2AMessage.class);
        assertThat(parsed.metadata()).isNull();
    }
}
