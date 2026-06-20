package io.casehub.qhorus.slack;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.InboundHumanMessage;
import io.casehub.qhorus.api.gateway.InboundNormaliser;
import io.casehub.qhorus.api.gateway.NormalisedMessage;
import io.casehub.qhorus.api.message.MessageType;

/**
 * Infers message type from Slack thread metadata.
 *
 * <p>The correlationId is resolved by {@link SlackChannelBackend#onInboundMessage} before
 * the gateway call — this normaliser simply passes it through and infers the type.
 */
@ApplicationScoped
public class SlackInboundNormaliser implements InboundNormaliser {

    @Override
    public NormalisedMessage normalise(ChannelRef channel, InboundHumanMessage raw) {
        String slackThreadTs = raw.metadata().get("slack-thread-ts");
        String slackTs = raw.metadata().get("slack-ts");
        String content = raw.content();

        // COMMAND:  slash command (content starts with "/")
        // RESPONSE: thread reply with a resolved correlationId (ongoing conversation)
        // QUERY:    new top-level message, or reply to an unknown thread
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
                "human:" + raw.externalSenderId(),
                raw.correlationId(),
                null,
                null,
                null);
    }
}
