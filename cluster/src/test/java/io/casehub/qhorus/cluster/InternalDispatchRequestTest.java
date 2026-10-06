package io.casehub.qhorus.cluster;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InternalDispatchRequestTest {

    @Test
    void roundTripPreservesAllFields() {
        UUID channelId = UUID.randomUUID();
        var original = MessageDispatch.builder()
                .channelId(channelId)
                .sender("agent-1")
                .type(MessageType.COMMAND)
                .content("do something")
                .correlationId("corr-1")
                .target("role:worker")
                .actorType(ActorType.AGENT)
                .topic("general")
                .build();

        var request = InternalDispatchRequest.from(original);
        var restored = request.toMessageDispatch();

        assertThat(restored.channelId()).isEqualTo(channelId);
        assertThat(restored.sender()).isEqualTo("agent-1");
        assertThat(restored.type()).isEqualTo(MessageType.COMMAND);
        assertThat(restored.content()).isEqualTo("do something");
        assertThat(restored.correlationId()).isEqualTo("corr-1");
        assertThat(restored.target()).isEqualTo("role:worker");
        assertThat(restored.actorType()).isEqualTo(ActorType.AGENT);
        assertThat(restored.topic()).isEqualTo("general");
    }

    @Test
    void roundTripHandlesNullFields() {
        UUID channelId = UUID.randomUUID();
        var original = MessageDispatch.builder()
                .channelId(channelId)
                .sender("agent-1")
                .type(MessageType.STATUS)
                .content("status update")
                .actorType(ActorType.AGENT)
                .build();

        var request = InternalDispatchRequest.from(original);
        var restored = request.toMessageDispatch();

        assertThat(restored.channelId()).isEqualTo(channelId);
        assertThat(restored.correlationId()).isNull();
        assertThat(restored.target()).isNull();
        assertThat(restored.topic()).isEqualTo("general");
        assertThat(restored.subjectId()).isNull();
        assertThat(restored.deadline()).isNull();
    }
}
