package io.casehub.qhorus.slack.core;

import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.InboundHumanMessage;
import io.casehub.qhorus.api.gateway.InboundNormaliser;
import io.casehub.qhorus.api.gateway.NormalisedMessage;
import io.casehub.qhorus.api.message.MessageType;

public class SlackInboundNormaliser implements InboundNormaliser {

    @Override
    public NormalisedMessage normalise(ChannelRef channel, InboundHumanMessage raw) {
        String slackThreadTs = raw.metadata().get("slack-thread-ts");
        String slackTs = raw.metadata().get("slack-ts");
        String content = raw.content();

        final MessageType type;
        if (content != null && content.startsWith("/")) {
            type = MessageType.COMMAND;
        } else if (slackThreadTs != null && !slackThreadTs.equals(slackTs)
                   && raw.correlationId() != null) {
            type = MessageType.RESPONSE;
        } else {
            type = MessageType.QUERY;
        }

        return new NormalisedMessage(
                type,
                content,
                null,
                "human:" + raw.externalSenderId(),
                raw.correlationId(),
                null,
                null,
                null);
    }
}
