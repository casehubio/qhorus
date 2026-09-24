package io.casehub.qhorus.runtime.broadcast;

import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BroadcastDispatchTest {

    @Test
    void defaultBroadcast_throwsUnsupported() {
        MessageDispatcher dispatcher = dispatch -> null;

        assertThatThrownBy(() ->
                dispatcher.broadcast("analyzer", MessageType.STATUS, "test", "agent-1", "default"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void broadcastMethodSignature_acceptsAllParameters() {
        MessageDispatcher dispatcher = new MessageDispatcher() {
            @Override
            public DispatchResult dispatch(io.casehub.qhorus.api.message.MessageDispatch dispatch) {
                return null;
            }

            @Override
            public DispatchResult broadcast(String capabilityTag, MessageType type, String content,
                                             String sender, String tenancyId) {
                assertThat(capabilityTag).isEqualTo("analyzer");
                assertThat(type).isEqualTo(MessageType.STATUS);
                assertThat(content).isEqualTo("anomaly detected");
                assertThat(sender).isEqualTo("agent-1");
                assertThat(tenancyId).isEqualTo("default");
                return new DispatchResult(1L, null, sender, type, null, null, null, null, null, null, null, 0, null);
            }
        };

        DispatchResult result = dispatcher.broadcast("analyzer", MessageType.STATUS,
                "anomaly detected", "agent-1", "default");
        assertThat(result).isNotNull();
        assertThat(result.messageId()).isEqualTo(1L);
    }
}
