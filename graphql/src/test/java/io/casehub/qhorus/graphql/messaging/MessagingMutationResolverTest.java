package io.casehub.qhorus.graphql.messaging;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.ReactionManager;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.Reaction;
import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.graphql.dto.CancelWaitResultType;
import io.casehub.qhorus.graphql.dto.DeleteMessageResultType;
import io.casehub.qhorus.graphql.dto.DispatchMessageInput;
import io.casehub.qhorus.graphql.dto.DispatchResultType;
import io.casehub.qhorus.graphql.dto.ReactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagingMutationResolverTest {

    private MessagingMutationResolver resolver;
    private MessageDispatcher messageDispatcher;
    private CurrentPrincipal currentPrincipal;
    private ReactionManager reactionManager;
    private MessageStore messageStore;
    private ConsumerMessaging consumerMessaging;
    private CommitmentStore commitmentStore;

    @BeforeEach
    void setUp() {
        messageDispatcher = mock(MessageDispatcher.class);
        currentPrincipal = mock(CurrentPrincipal.class);
        reactionManager = mock(ReactionManager.class);
        messageStore = mock(MessageStore.class);
        consumerMessaging = mock(ConsumerMessaging.class);
        commitmentStore = mock(CommitmentStore.class);

        when(currentPrincipal.actorId()).thenReturn("test-actor");
        when(currentPrincipal.tenancyId()).thenReturn("default");

        resolver = new MessagingMutationResolver();
        resolver.messageDispatcher = messageDispatcher;
        resolver.currentPrincipal = currentPrincipal;
        resolver.reactionManager = reactionManager;
        resolver.messageStore = messageStore;
        resolver.consumerMessaging = consumerMessaging;
        resolver.commitmentStore = commitmentStore;
    }

    // --- dispatchMessage ---

    @Test
    void dispatchMessageBuildsDispatchFromInput() {
        UUID channelId = UUID.randomUUID();
        DispatchResult result = dispatchResult(1L, channelId, "test-actor", MessageType.STATUS);
        when(messageDispatcher.dispatch(any(MessageDispatch.class))).thenReturn(result);

        DispatchMessageInput input = new DispatchMessageInput(
                channelId, "STATUS", "hello", null, null, null, null, null);
        DispatchResultType actual = resolver.dispatchMessage(input);

        assertThat(actual).isNotNull();
        ArgumentCaptor<MessageDispatch> captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().channelId()).isEqualTo(channelId);
        assertThat(captor.getValue().sender()).isEqualTo("test-actor");
        assertThat(captor.getValue().content()).isEqualTo("hello");
    }

    @Test
    void dispatchMessagePassesOptionalFields() {
        UUID channelId = UUID.randomUUID();
        DispatchResult result = dispatchResult(2L, channelId, "test-actor", MessageType.COMMAND);
        when(messageDispatcher.dispatch(any(MessageDispatch.class))).thenReturn(result);

        DispatchMessageInput input = new DispatchMessageInput(
                channelId, "COMMAND", "do something", "corr-1", null, "role:worker", "general", null);
        resolver.dispatchMessage(input);

        ArgumentCaptor<MessageDispatch> captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().correlationId()).isEqualTo("corr-1");
        assertThat(captor.getValue().target()).isEqualTo("role:worker");
        assertThat(captor.getValue().topic()).isEqualTo("general");
    }

    // --- deleteMessage ---

    @Test
    void deleteMessageReturnsNotFoundWhenMissing() {
        when(messageStore.find(99L)).thenReturn(Optional.empty());

        DeleteMessageResultType result = resolver.deleteMessage(99L);

        assertThat(result.deleted()).isFalse();
        assertThat(result.status()).contains("not found");
        verify(messageStore, never()).delete(any());
    }

    @Test
    void deleteMessageOrphansRepliesAndDeletes() {
        UUID channelId = UUID.randomUUID();
        Message msg = testMessage(10L, channelId, "agent-1", MessageType.STATUS, "some content");
        Message reply = testMessage(11L, channelId, "agent-2", MessageType.RESPONSE, "a reply");
        when(messageStore.find(10L)).thenReturn(Optional.of(msg));
        when(messageStore.scan(any(MessageQuery.class))).thenReturn(List.of(reply));
        when(messageDispatcher.dispatch(any(MessageDispatch.class)))
                .thenReturn(dispatchResult(12L, channelId, "system", MessageType.EVENT));

        DeleteMessageResultType result = resolver.deleteMessage(10L);

        assertThat(result.deleted()).isTrue();
        assertThat(result.sender()).isEqualTo("agent-1");
        assertThat(result.messageType()).isEqualTo("STATUS");
        assertThat(result.preview()).isEqualTo("some content");

        verify(messageStore).put(any(Message.class));
        verify(messageStore).delete(10L);
    }

    @Test
    void deleteMessageTruncatesLongPreview() {
        UUID channelId = UUID.randomUUID();
        String longContent = "x".repeat(120);
        Message msg = testMessage(10L, channelId, "agent", MessageType.STATUS, longContent);
        when(messageStore.find(10L)).thenReturn(Optional.of(msg));
        when(messageStore.scan(any(MessageQuery.class))).thenReturn(List.of());
        when(messageDispatcher.dispatch(any(MessageDispatch.class)))
                .thenReturn(dispatchResult(11L, channelId, "system", MessageType.EVENT));

        DeleteMessageResultType result = resolver.deleteMessage(10L);

        assertThat(result.preview()).hasSize(81);
    }

    // --- react / unreact ---

    @Test
    void reactDelegatesToReactionManager() {
        Reaction reaction = new Reaction(1L, 10L, "thumbsup", "test-actor", Instant.now(), "default");
        when(reactionManager.react(10L, "thumbsup")).thenReturn(reaction);

        ReactionType result = resolver.react(10L, "thumbsup");

        assertThat(result.emoji()).isEqualTo("thumbsup");
        assertThat(result.messageId()).isEqualTo(10L);
    }

    @Test
    void unreactDelegatesToReactionManager() {
        when(reactionManager.unreact(10L, "thumbsup")).thenReturn(true);

        boolean removed = resolver.unreact(10L, "thumbsup");

        assertThat(removed).isTrue();
    }

    // --- respondToApproval ---

    @Test
    void respondToApprovalDispatchesResponse() {
        UUID channelId = UUID.randomUUID();
        Message original = testMessage(5L, channelId, "agent", MessageType.QUERY, "approve?");
        when(consumerMessaging.findByCorrelationId("corr-1")).thenReturn(Optional.of(original));
        when(messageDispatcher.dispatch(any(MessageDispatch.class)))
                .thenReturn(dispatchResult(6L, channelId, "human", MessageType.RESPONSE));

        DispatchResultType result = resolver.respondToApproval("corr-1", "approved", channelId);

        assertThat(result).isNotNull();
        ArgumentCaptor<MessageDispatch> captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(MessageType.RESPONSE);
        assertThat(captor.getValue().correlationId()).isEqualTo("corr-1");
        assertThat(captor.getValue().inReplyTo()).isEqualTo(5L);
        assertThat(captor.getValue().content()).isEqualTo("approved");
    }

    @Test
    void respondToApprovalHandlesMissingOriginal() {
        UUID channelId = UUID.randomUUID();
        when(consumerMessaging.findByCorrelationId("corr-1")).thenReturn(Optional.empty());
        when(messageDispatcher.dispatch(any(MessageDispatch.class)))
                .thenReturn(dispatchResult(6L, channelId, "human", MessageType.RESPONSE));

        DispatchResultType result = resolver.respondToApproval("corr-1", "approved", channelId);

        assertThat(result).isNotNull();
        ArgumentCaptor<MessageDispatch> captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().inReplyTo()).isNull();
    }

    // --- cancelWait ---

    @Test
    void cancelWaitDeletesCommitment() {
        UUID commitmentId = UUID.randomUUID();
        Commitment commitment = testCommitment(commitmentId, "corr-1", CommitmentState.OPEN);
        when(commitmentStore.findByCorrelationId("corr-1")).thenReturn(Optional.of(commitment));

        CancelWaitResultType result = resolver.cancelWait("corr-1");

        assertThat(result.cancelled()).isTrue();
        verify(commitmentStore).deleteById(commitmentId);
    }

    @Test
    void cancelWaitReturnsFalseWhenNotFound() {
        when(commitmentStore.findByCorrelationId("corr-1")).thenReturn(Optional.empty());

        CancelWaitResultType result = resolver.cancelWait("corr-1");

        assertThat(result.cancelled()).isFalse();
    }

    // --- helpers ---

    private Message testMessage(Long id, UUID channelId, String sender, MessageType type, String content) {
        return new Message(id, channelId, sender, type,
                null, "default", content, null, null, null, 0, null, null, null,
                null, null, null, 0, Instant.now());
    }

    private Commitment testCommitment(UUID id, String correlationId, CommitmentState state) {
        return new Commitment(id, correlationId, UUID.randomUUID(), MessageType.QUERY,
                "requester", "obligor", state, null, null, null, null, null, "default", null, Instant.now());
    }

    private DispatchResult dispatchResult(Long messageId, UUID channelId, String sender, MessageType type) {
        return new DispatchResult(messageId, channelId, sender, type,
                null, null, List.of(), null, null, null, null, 0, List.of());
    }
}
