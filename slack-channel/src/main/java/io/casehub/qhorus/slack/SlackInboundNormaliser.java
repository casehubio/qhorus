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

        // RESPONSE: thread reply with a resolved correlationId (ongoing conversation)
        // QUERY:    new top-level message, or reply to an unknown thread
        MessageType type = (slackThreadTs != null && !slackThreadTs.equals(slackTs)
                && raw.correlationId() != null)
                ? MessageType.RESPONSE
                : MessageType.QUERY;

        return new NormalisedMessage(
                type,
                raw.content(),
                "human:" + raw.externalSenderId(),
                raw.correlationId(),
                null,
                null,
                null);
    }
}
