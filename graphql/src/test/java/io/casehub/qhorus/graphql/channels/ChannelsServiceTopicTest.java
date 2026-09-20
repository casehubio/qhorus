package io.casehub.qhorus.graphql.channels;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.MembershipManager;
import io.casehub.qhorus.api.channel.SpaceManager;
import io.casehub.qhorus.api.channel.TopicManager;
import io.casehub.qhorus.api.channel.TopicMergeResult;
import io.casehub.qhorus.api.channel.TopicMoveResult;
import io.casehub.qhorus.api.channel.TopicRenameResult;
import io.casehub.qhorus.api.channel.UnreadCountProvider;
import io.casehub.qhorus.api.gateway.BackendRegistry;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.Topic;
import io.casehub.qhorus.api.message.TopicSummary;
import io.casehub.qhorus.api.store.MessageReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelsServiceTopicTest {

    private ChannelsService service;
    private TopicManager topicManager;

    @BeforeEach
    void setUp() {
        topicManager = mock(TopicManager.class);
        service = new ChannelsService(
                mock(ChannelReader.class), mock(ConsumerMessaging.class),
                mock(ChannelManager.class), topicManager,
                mock(MembershipManager.class), mock(UnreadCountProvider.class),
                mock(SpaceManager.class), mock(BackendRegistry.class),
                mock(MessageReader.class), mock(CurrentPrincipal.class));
    }

    @Test
    void topicsListsDelegatesToManager() {
        UUID channelId = UUID.randomUUID();
        var summary = new TopicSummary("bugs", 5, Instant.now(), false, null);
        when(topicManager.listTopics(channelId)).thenReturn(List.of(summary));

        var result = service.topics(channelId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("bugs");
    }

    @Test
    void resolveTopicDelegatesToManager() {
        UUID channelId = UUID.randomUUID();
        var topic = new Topic(1L, channelId, "bugs", true, Instant.now(), "actor-1", Instant.now(), "t");
        when(topicManager.resolve(channelId, "bugs", "actor-1")).thenReturn(topic);

        var result = service.resolveTopic(channelId, "bugs", "actor-1");

        assertThat(result.resolved()).isTrue();
    }

    @Test
    void resolveTopicDefaultsActorToAnonymous() {
        UUID channelId = UUID.randomUUID();
        var topic = new Topic(1L, channelId, "bugs", true, Instant.now(), "anonymous", Instant.now(), "t");
        when(topicManager.resolve(channelId, "bugs", "anonymous")).thenReturn(topic);

        service.resolveTopic(channelId, "bugs", null);

        verify(topicManager).resolve(channelId, "bugs", "anonymous");
    }

    @Test
    void unresolveTopicDelegates() {
        UUID channelId = UUID.randomUUID();
        var topic = new Topic(1L, channelId, "bugs", false, null, null, Instant.now(), "t");
        when(topicManager.unresolve(channelId, "bugs")).thenReturn(topic);

        var result = service.unresolveTopic(channelId, "bugs");

        assertThat(result.resolved()).isFalse();
    }

    @Test
    void renameTopicDelegates() {
        UUID channelId = UUID.randomUUID();
        var inner = new TopicManager.RenameResult("old", "new", 3);
        when(topicManager.rename(channelId, "old", "new", "actor-1")).thenReturn(inner);

        TopicRenameResult result = service.renameTopic(channelId, "old", "new", "actor-1");

        assertThat(result.newName()).isEqualTo("new");
        assertThat(result.messagesUpdated()).isEqualTo(3);
    }

    @Test
    void mergeTopicsDelegates() {
        UUID channelId = UUID.randomUUID();
        var inner = new TopicManager.MergeResult("source", "target", 5);
        when(topicManager.merge(channelId, "source", "target", "actor-1")).thenReturn(inner);

        TopicMergeResult result = service.mergeTopics(channelId, "source", "target", "actor-1");

        assertThat(result.sourceTopic()).isEqualTo("source");
        assertThat(result.messagesUpdated()).isEqualTo(5);
    }

    @Test
    void moveTopicDelegates() {
        UUID src = UUID.randomUUID();
        UUID tgt = UUID.randomUUID();
        var inner = new TopicManager.MoveResult("bugs", src, tgt, 7);
        when(topicManager.move(src, "bugs", tgt)).thenReturn(inner);

        TopicMoveResult result = service.moveTopic(src, "bugs", tgt, "actor-1");

        assertThat(result.topicName()).isEqualTo("bugs");
        assertThat(result.messagesUpdated()).isEqualTo(7);
    }
}
