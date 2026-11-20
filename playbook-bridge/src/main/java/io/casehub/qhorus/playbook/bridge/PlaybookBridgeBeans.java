package io.casehub.qhorus.playbook.bridge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.message.protocol.ProtocolRegistry;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PlaybookBridgeBeans {

    private static final Logger LOG = Logger.getLogger(PlaybookBridgeBeans.class);

    @Inject
    MessageDispatcher messageDispatcher;

    @Produces
    @ApplicationScoped
    public ProtocolDeadlineScheduler protocolDeadlineScheduler() {
        return new ProtocolDeadlineScheduler(this::fireDeadlineEvent);
    }

    private void fireDeadlineEvent(DeadlineKey key) {
        String telemetry = "{\"tool_name\":\"protocol-deadline\","
                + "\"protocol_name\":\"" + key.protocolName() + "\","
                + "\"deadline_name\":\"" + key.deadlineName() + "\"}";

        MessageDispatch dispatch = MessageDispatch.builder()
                .channelId(key.channelId())
                .sender("system:protocol-timer")
                .type(MessageType.EVENT)
                .telemetry(telemetry)
                .build();
        messageDispatcher.dispatch(dispatch);
    }
}
