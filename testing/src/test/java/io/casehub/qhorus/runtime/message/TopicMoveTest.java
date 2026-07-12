package io.casehub.qhorus.runtime.message;

import io.casehub.qhorus.persistence.memory.InMemoryCommitmentStore;
import io.casehub.qhorus.persistence.memory.InMemoryMessageStore;
import io.casehub.qhorus.persistence.memory.InMemoryTopicStore;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.Topic;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class TopicMoveTest {

    private TopicService topicService;
    private InMemoryTopicStore topicStore;
    private InMemoryMessageStore messageStore;
    private InMemoryCommitmentStore commitmentStore;

    private final UUID srcChannel = UUID.randomUUID();
    private final UUID tgtChannel = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        topicStore = new InMemoryTopicStore();
        messageStore = new InMemoryMessageStore();
        commitmentStore = new InMemoryCommitmentStore();
        topicService = new TopicService();
        topicService.topicStore = topicStore;
        topicService.messageStore = messageStore;
        topicService.commitmentStore = commitmentStore;
    }

    private void addTopic(UUID ch, String name) {
        topicStore.put(new Topic(null, ch, name, false, null, null, Instant.now(), null));
    }

    private void addMessage(UUID ch, String topic) {
        messageStore.put(Message.builder()
                .channelId(ch).sender("agent").messageType(MessageType.STATUS)
                .actorType(ActorType.AGENT).content("msg").topic(topic).build());
    }

    @Test
    void moveUpdatesChannelIdAndCreatesTopicInTarget() {
        addTopic(srcChannel, "bugs");
        addMessage(srcChannel, "bugs");
        addMessage(srcChannel, "bugs");

        TopicService.MoveResult result = topicService.move(srcChannel, "bugs", tgtChannel, "admin");

        assertThat(result.messagesUpdated()).isEqualTo(2);
        assertThat(result.sourceChannelId()).isEqualTo(srcChannel);
        assertThat(result.targetChannelId()).isEqualTo(tgtChannel);
        assertThat(topicStore.find(srcChannel, "bugs")).isEmpty();
        assertThat(topicStore.find(tgtChannel, "bugs")).isPresent();
    }

    @Test
    void moveMergesIntoExistingTargetTopic() {
        addTopic(srcChannel, "bugs");
        addTopic(tgtChannel, "bugs");
        addMessage(srcChannel, "bugs");

        TopicService.MoveResult result = topicService.move(srcChannel, "bugs", tgtChannel, "admin");

        assertThat(result.messagesUpdated()).isEqualTo(1);
        assertThat(topicStore.find(srcChannel, "bugs")).isEmpty();
        assertThat(topicStore.find(tgtChannel, "bugs")).isPresent();
    }

    @Test
    void moveBlockedByOpenCommitment() {
        addTopic(srcChannel, "bugs");
        var saved = commitmentStore.save(Commitment.builder()
                .correlationId("corr-1").channelId(srcChannel).requester("r")
                .state(CommitmentState.OPEN).createdAt(Instant.now()).build());
        messageStore.put(Message.builder()
                .channelId(srcChannel).sender("agent").messageType(MessageType.COMMAND)
                .actorType(ActorType.AGENT).content("cmd").topic("bugs")
                .commitmentId(saved.id()).build());

        assertThatThrownBy(() -> topicService.move(srcChannel, "bugs", tgtChannel, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("open commitments");
    }

    @Test
    void moveAllowedWithTerminalCommitments() {
        addTopic(srcChannel, "bugs");
        var saved = commitmentStore.save(Commitment.builder()
                .correlationId("corr-2").channelId(srcChannel).requester("r")
                .state(CommitmentState.FULFILLED).createdAt(Instant.now()).build());
        messageStore.put(Message.builder()
                .channelId(srcChannel).sender("agent").messageType(MessageType.COMMAND)
                .actorType(ActorType.AGENT).content("cmd").topic("bugs")
                .commitmentId(saved.id()).build());

        TopicService.MoveResult result = topicService.move(srcChannel, "bugs", tgtChannel, "admin");
        assertThat(result.messagesUpdated()).isEqualTo(1);
    }

    @Test
    void moveGeneralRejected() {
        topicService.ensureExists(srcChannel, "general", null);

        assertThatThrownBy(() -> topicService.move(srcChannel, "general", tgtChannel, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("general");
    }

    @Test
    void moveSourceNotFoundRejected() {
        assertThatThrownBy(() -> topicService.move(srcChannel, "nonexistent", tgtChannel, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void moveNoMessagesReturnsZero() {
        addTopic(srcChannel, "empty-topic");

        TopicService.MoveResult result = topicService.move(srcChannel, "empty-topic", tgtChannel, "admin");

        assertThat(result.messagesUpdated()).isZero();
        assertThat(topicStore.find(srcChannel, "empty-topic")).isEmpty();
        assertThat(topicStore.find(tgtChannel, "empty-topic")).isPresent();
    }
}
