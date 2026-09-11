package io.casehub.qhorus.graphql.messaging;

import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.Reaction;
import io.casehub.qhorus.api.store.MessageReader;
import io.casehub.qhorus.api.store.ReactionReader;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.graphql.dto.MessageReactionsType;
import io.casehub.qhorus.graphql.dto.ReactionGroupType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagingQueryResolverTest {

    private MessagingQueryResolver resolver;
    private ConsumerMessaging consumerMessaging;
    private MessageReader messageReader;
    private ReactionReader reactionReader;

    @BeforeEach
    void setUp() {
        consumerMessaging = mock(ConsumerMessaging.class);
        messageReader = mock(MessageReader.class);
        reactionReader = mock(ReactionReader.class);
        resolver = new MessagingQueryResolver();
        resolver.consumerMessaging = consumerMessaging;
        resolver.messageReader = messageReader;
        resolver.reactionReader = reactionReader;
    }

    @Test
    void messageReturnsFoundMessage() {
        Message msg = testMessage(42L, "hello");
        when(consumerMessaging.findById(42L)).thenReturn(Optional.of(msg));

        var result = resolver.message(42L);

        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.content()).isEqualTo("hello");
    }

    @Test
    void messageThrowsWhenNotFound() {
        when(consumerMessaging.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.message(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void repliesQueriesWithInReplyTo() {
        Message reply = testMessage(2L, "reply");
        when(messageReader.scan(any(MessageQuery.class))).thenReturn(List.of(reply));

        var results = resolver.replies(1L, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().content()).isEqualTo("reply");

        ArgumentCaptor<MessageQuery> captor = ArgumentCaptor.forClass(MessageQuery.class);
        verify(messageReader).scan(captor.capture());
        assertThat(captor.getValue().inReplyTo()).isEqualTo(1L);
        assertThat(captor.getValue().limit()).isEqualTo(20);
    }

    @Test
    void repliesRespectsLimitAndAfterId() {
        when(messageReader.scan(any(MessageQuery.class))).thenReturn(List.of());

        resolver.replies(1L, 5L, 50);

        ArgumentCaptor<MessageQuery> captor = ArgumentCaptor.forClass(MessageQuery.class);
        verify(messageReader).scan(captor.capture());
        assertThat(captor.getValue().afterId()).isEqualTo(5L);
        assertThat(captor.getValue().limit()).isEqualTo(50);
    }

    @Test
    void repliesCapsLimitAt100() {
        when(messageReader.scan(any(MessageQuery.class))).thenReturn(List.of());

        resolver.replies(1L, null, 999);

        ArgumentCaptor<MessageQuery> captor = ArgumentCaptor.forClass(MessageQuery.class);
        verify(messageReader).scan(captor.capture());
        assertThat(captor.getValue().limit()).isEqualTo(100);
    }

    @Test
    void searchMessagesExcludesEvents() {
        Message msg = testMessage(1L, "found it");
        when(messageReader.scan(any(MessageQuery.class))).thenReturn(List.of(msg));

        var results = resolver.searchMessages("found", null, null);

        assertThat(results).hasSize(1);

        ArgumentCaptor<MessageQuery> captor = ArgumentCaptor.forClass(MessageQuery.class);
        verify(messageReader).scan(captor.capture());
        assertThat(captor.getValue().contentPattern()).isEqualTo("found");
        assertThat(captor.getValue().excludeTypes()).contains(MessageType.EVENT);
        assertThat(captor.getValue().limit()).isEqualTo(20);
    }

    @Test
    void searchMessagesFiltersbyChannel() {
        UUID channelId = UUID.randomUUID();
        when(messageReader.scan(any(MessageQuery.class))).thenReturn(List.of());

        resolver.searchMessages("query", channelId, 10);

        ArgumentCaptor<MessageQuery> captor = ArgumentCaptor.forClass(MessageQuery.class);
        verify(messageReader).scan(captor.capture());
        assertThat(captor.getValue().channelId()).isEqualTo(channelId);
        assertThat(captor.getValue().limit()).isEqualTo(10);
    }

    @Test
    void reactionsGroupsByEmoji() {
        List<Reaction> reactions = List.of(
                new Reaction(1L, 10L, "thumbsup", "alice", Instant.now(), "default"),
                new Reaction(2L, 10L, "thumbsup", "bob", Instant.now(), "default"),
                new Reaction(3L, 10L, "heart", "alice", Instant.now(), "default"));
        when(reactionReader.findByMessage(10L)).thenReturn(reactions);

        List<ReactionGroupType> result = resolver.reactions(10L);

        assertThat(result).hasSize(2);
        ReactionGroupType thumbsup = result.stream()
                .filter(g -> g.emoji().equals("thumbsup")).findFirst().orElseThrow();
        assertThat(thumbsup.count()).isEqualTo(2);
        assertThat(thumbsup.actorIds()).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void reactionsBatchReturnsPerMessageGrouping() {
        List<Reaction> msg10Reactions = List.of(
                new Reaction(1L, 10L, "thumbsup", "alice", Instant.now(), "default"));
        when(reactionReader.findByMessages(any())).thenReturn(Map.of(10L, msg10Reactions));

        List<MessageReactionsType> result = resolver.reactionsBatch(List.of(10L, 20L));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).messageId()).isEqualTo(10L);
        assertThat(result.get(0).reactions()).hasSize(1);
        assertThat(result.get(1).messageId()).isEqualTo(20L);
        assertThat(result.get(1).reactions()).isEmpty();
    }

    @Test
    void reactionsBatchRejectsEmptyList() {
        assertThatThrownBy(() -> resolver.reactionsBatch(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reactionsBatchRejectsOver200() {
        List<Long> ids = new java.util.ArrayList<>();
        for (long i = 0; i < 201; i++) ids.add(i);

        assertThatThrownBy(() -> resolver.reactionsBatch(ids))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");
    }

    private Message testMessage(Long id, String content) {
        return new Message(id, UUID.randomUUID(), "test-sender", MessageType.STATUS,
                null, "default", content, null, null, null, 0, null, null, null,
                null, null, null, 0, Instant.now());
    }
}
