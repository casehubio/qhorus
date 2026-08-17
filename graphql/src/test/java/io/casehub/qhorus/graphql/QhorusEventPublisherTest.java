package io.casehub.qhorus.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.qhorus.api.channel.PresenceChangedEvent;
import io.casehub.qhorus.api.channel.PresenceStatus;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QhorusEventPublisherTest {

    @Test
    void messageStreamReceivesEvents() {
        var publisher = new QhorusEventPublisher();
        List<MessageReceivedEvent> received = new ArrayList<>();

        publisher.messageStream().subscribe().with(received::add);

        var event = new MessageReceivedEvent(
                1L, "test-channel", UUID.randomUUID(), "tenant",
                MessageType.STATUS, "sender", null, null, null,
                Instant.now(), "hello", "general");
        publisher.onMessageReceived(event);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).messageId()).isEqualTo(1L);
    }

    @Test
    void presenceStreamReceivesEvents() {
        var publisher = new QhorusEventPublisher();
        List<PresenceChangedEvent> received = new ArrayList<>();

        publisher.presenceStream().subscribe().with(received::add);

        var event = new PresenceChangedEvent(
                "member-1", UUID.randomUUID(),
                PresenceStatus.ONLINE, PresenceStatus.OFFLINE, Instant.now());
        publisher.onPresenceChanged(event);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).memberId()).isEqualTo("member-1");
    }

    @Test
    void multipleSubscribersReceiveEvents() {
        var publisher = new QhorusEventPublisher();
        List<MessageReceivedEvent> sub1 = new ArrayList<>();
        List<MessageReceivedEvent> sub2 = new ArrayList<>();

        publisher.messageStream().subscribe().with(sub1::add);
        publisher.messageStream().subscribe().with(sub2::add);

        var event = new MessageReceivedEvent(
                1L, "ch", UUID.randomUUID(), "tenant",
                MessageType.STATUS, "sender", null, null, null,
                Instant.now(), "content", "general");
        publisher.onMessageReceived(event);

        assertThat(sub1).hasSize(1);
        assertThat(sub2).hasSize(1);
    }
}
