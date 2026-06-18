package io.casehub.qhorus.slack;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.InboundHumanMessage;
import io.casehub.qhorus.api.gateway.NormalisedMessage;
import io.casehub.qhorus.api.message.MessageType;

class SlackInboundNormaliserTest {

    private final SlackInboundNormaliser normaliser = new SlackInboundNormaliser();
    private final ChannelRef channel = new ChannelRef(UUID.randomUUID(), "test-channel");

    @Test
    void newTopLevelMessage_producesQuery() {
        InboundHumanMessage msg = new InboundHumanMessage(
                "U123", "Hello", Instant.now(), Map.of("slack-ts", "1.1"), null, null);
        NormalisedMessage result = normaliser.normalise(channel, msg);
        assertThat(result.type()).isEqualTo(MessageType.QUERY);
    }

    @Test
    void threadRootByHuman_slackThreadTsEqualsSlackTs_producesQuery() {
        InboundHumanMessage msg = new InboundHumanMessage(
                "U123", "Root", Instant.now(),
                Map.of("slack-ts", "1.1", "slack-thread-ts", "1.1"), null, null);
        NormalisedMessage result = normaliser.normalise(channel, msg);
        assertThat(result.type()).isEqualTo(MessageType.QUERY);
    }

    @Test
    void threadReplyWithResolvedCorrId_producesResponse() {
        String corrId = UUID.randomUUID().toString();
        InboundHumanMessage msg = new InboundHumanMessage(
                "U123", "Reply", Instant.now(),
                Map.of("slack-ts", "1.2", "slack-thread-ts", "1.1"), corrId, null);
        NormalisedMessage result = normaliser.normalise(channel, msg);
        assertThat(result.type()).isEqualTo(MessageType.RESPONSE);
        assertThat(result.correlationId()).isEqualTo(corrId);
    }

    @Test
    void threadReplyWithoutCorrId_producesQuery() {
        // Thread reply to an unknown thread — corrId not resolved
        InboundHumanMessage msg = new InboundHumanMessage(
                "U123", "Reply to unknown", Instant.now(),
                Map.of("slack-ts", "1.2", "slack-thread-ts", "1.1"), null, null);
        NormalisedMessage result = normaliser.normalise(channel, msg);
        assertThat(result.type()).isEqualTo(MessageType.QUERY);
    }

    @Test
    void senderPrefix_isHumanColon() {
        InboundHumanMessage msg = new InboundHumanMessage(
                "U999", "Hi", Instant.now(), Map.of(), null, null);
        NormalisedMessage result = normaliser.normalise(channel, msg);
        assertThat(result.senderInstanceId()).isEqualTo("human:U999");
    }

    @Test
    void correlationId_isPassedThrough() {
        String corrId = UUID.randomUUID().toString();
        InboundHumanMessage msg = new InboundHumanMessage(
                "U123", "text", Instant.now(), Map.of(), corrId, null);
        NormalisedMessage result = normaliser.normalise(channel, msg);
        assertThat(result.correlationId()).isEqualTo(corrId);
    }
}
