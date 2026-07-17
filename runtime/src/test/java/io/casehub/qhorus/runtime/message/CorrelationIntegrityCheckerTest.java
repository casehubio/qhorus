package io.casehub.qhorus.runtime.message;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.qhorus.api.store.MessageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CorrelationIntegrityCheckerTest {

    private CorrelationIntegrityChecker checker;
    private CommitmentStore             commitmentStore;
    private MessageStore                messageStore;
    private UUID                        channelId;

    @BeforeEach
    void setUp() {
        commitmentStore         = mock(CommitmentStore.class);
        messageStore            = mock(MessageStore.class);
        checker                 = new CorrelationIntegrityChecker();
        checker.commitmentStore = commitmentStore;
        checker.messageStore    = messageStore;
        channelId               = UUID.randomUUID();

        // Default: inReplyTo(1L) resolves to a valid same-channel message
        when(messageStore.find(1L)).thenReturn(Optional.of(Message.builder()
                                                                  .id(1L).channelId(channelId).sender("origin").messageType(MessageType.COMMAND)
                                                                  .actorType(ActorType.AGENT).content("origin message").build()));
    }

    // --- inReplyTo validation ---

    @Test
    void inReplyTo_nonExistentMessage_advisory() {
        when(messageStore.find(999L)).thenReturn(Optional.empty());

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-a").type(MessageType.STATUS)
                                                  .content("update").inReplyTo(999L).actorType(ActorType.AGENT).build();

        List<String> advisories = checker.check(dispatch, channelId);

        assertThat(advisories).hasSize(1);
        assertThat(advisories.get(0)).contains("inReplyTo").contains("999");
    }

    @Test
    void inReplyTo_wrongChannel_advisory() {
        UUID otherChannel = UUID.randomUUID();
        Message parent = Message.builder()
                                .id(2L).channelId(otherChannel).sender("agent-b").messageType(MessageType.COMMAND)
                                .actorType(ActorType.AGENT).content("do something").build();
        when(messageStore.find(2L)).thenReturn(Optional.of(parent));

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-a").type(MessageType.STATUS)
                                                  .content("reply").inReplyTo(2L).actorType(ActorType.AGENT).build();

        List<String> advisories = checker.check(dispatch, channelId);

        assertThat(advisories).hasSize(1);
        assertThat(advisories.get(0)).contains("different channel");
    }

    @Test
    void inReplyTo_validSameChannel_noAdvisory() {
        Message parent = Message.builder()
                                .id(1L).channelId(channelId).sender("agent-b").messageType(MessageType.COMMAND)
                                .actorType(ActorType.AGENT).content("do something").build();
        when(messageStore.find(1L)).thenReturn(Optional.of(parent));

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-a").type(MessageType.STATUS)
                                                  .content("working").inReplyTo(1L).actorType(ActorType.AGENT).build();

        assertThat(checker.check(dispatch, channelId)).isEmpty();
    }

    @Test
    void inReplyTo_null_noCheck() {
        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-a").type(MessageType.STATUS)
                                                  .content("update").actorType(ActorType.AGENT).build();

        assertThat(checker.check(dispatch, channelId)).isEmpty();
    }

    // --- Resolution type matching ---

    @Test
    void responseOnCommandObligation_advisory() {
        String corrId = UUID.randomUUID().toString();
        when(commitmentStore.findByCorrelationId(corrId)).thenReturn(Optional.of(
                Commitment.builder()
                          .id(UUID.randomUUID()).correlationId(corrId).channelId(channelId)
                          .messageType(MessageType.COMMAND).requester("requester").obligor("agent-a")
                          .state(CommitmentState.OPEN).build()));

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-a").type(MessageType.RESPONSE)
                                                  .content("answer").correlationId(corrId).inReplyTo(1L)
                                                  .actorType(ActorType.AGENT).build();

        List<String> advisories = checker.check(dispatch, channelId);

        assertThat(advisories).anyMatch(a -> a.contains("RESPONSE") && a.contains("COMMAND"));
    }

    @Test
    void doneOnQueryObligation_advisory() {
        String corrId = UUID.randomUUID().toString();
        when(commitmentStore.findByCorrelationId(corrId)).thenReturn(Optional.of(
                Commitment.builder()
                          .id(UUID.randomUUID()).correlationId(corrId).channelId(channelId)
                          .messageType(MessageType.QUERY).requester("requester").obligor("agent-a")
                          .state(CommitmentState.OPEN).build()));

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-a").type(MessageType.DONE)
                                                  .content("finished").correlationId(corrId).inReplyTo(1L)
                                                  .actorType(ActorType.AGENT).build();

        List<String> advisories = checker.check(dispatch, channelId);

        assertThat(advisories).anyMatch(a -> a.contains("DONE") && a.contains("QUERY"));
    }

    @Test
    void failureOnQueryObligation_advisory() {
        String corrId = UUID.randomUUID().toString();
        when(commitmentStore.findByCorrelationId(corrId)).thenReturn(Optional.of(
                Commitment.builder()
                          .id(UUID.randomUUID()).correlationId(corrId).channelId(channelId)
                          .messageType(MessageType.QUERY).requester("requester").obligor("agent-a")
                          .state(CommitmentState.OPEN).build()));

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-a").type(MessageType.FAILURE)
                                                  .content("failed").correlationId(corrId).inReplyTo(1L)
                                                  .actorType(ActorType.AGENT).build();

        List<String> advisories = checker.check(dispatch, channelId);

        assertThat(advisories).anyMatch(a -> a.contains("FAILURE") && a.contains("QUERY"));
    }

    @Test
    void doneOnCommandObligation_noAdvisory() {
        String corrId = UUID.randomUUID().toString();
        when(commitmentStore.findByCorrelationId(corrId)).thenReturn(Optional.of(
                Commitment.builder()
                          .id(UUID.randomUUID()).correlationId(corrId).channelId(channelId)
                          .messageType(MessageType.COMMAND).requester("requester").obligor("agent-a")
                          .state(CommitmentState.OPEN).build()));

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-a").type(MessageType.DONE)
                                                  .content("done").correlationId(corrId).inReplyTo(1L)
                                                  .actorType(ActorType.AGENT).build();

        assertThat(checker.check(dispatch, channelId)).isEmpty();
    }

    @Test
    void responseOnQueryObligation_noAdvisory() {
        String corrId = UUID.randomUUID().toString();
        when(commitmentStore.findByCorrelationId(corrId)).thenReturn(Optional.of(
                Commitment.builder()
                          .id(UUID.randomUUID()).correlationId(corrId).channelId(channelId)
                          .messageType(MessageType.QUERY).requester("requester").obligor("agent-a")
                          .state(CommitmentState.OPEN).build()));

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-a").type(MessageType.RESPONSE)
                                                  .content("answer").correlationId(corrId).inReplyTo(1L)
                                                  .actorType(ActorType.AGENT).build();

        assertThat(checker.check(dispatch, channelId)).isEmpty();
    }

    @Test
    void declineOnEitherObligationType_noAdvisory() {
        String corrId = UUID.randomUUID().toString();
        when(commitmentStore.findByCorrelationId(corrId)).thenReturn(Optional.of(
                Commitment.builder()
                          .id(UUID.randomUUID()).correlationId(corrId).channelId(channelId)
                          .messageType(MessageType.COMMAND).requester("requester").obligor("agent-a")
                          .state(CommitmentState.OPEN).build()));

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-a").type(MessageType.DECLINE)
                                                  .content("can't").correlationId(corrId).inReplyTo(1L)
                                                  .actorType(ActorType.AGENT).build();

        assertThat(checker.check(dispatch, channelId)).isEmpty();
    }

    // --- Obligor identity ---

    @Test
    void wrongSenderResolvingObligation_advisory() {
        String corrId = UUID.randomUUID().toString();
        when(commitmentStore.findByCorrelationId(corrId)).thenReturn(Optional.of(
                Commitment.builder()
                          .id(UUID.randomUUID()).correlationId(corrId).channelId(channelId)
                          .messageType(MessageType.COMMAND).requester("requester").obligor("agent-a")
                          .state(CommitmentState.OPEN).build()));

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-b").type(MessageType.DONE)
                                                  .content("done").correlationId(corrId).inReplyTo(1L)
                                                  .actorType(ActorType.AGENT).build();

        List<String> advisories = checker.check(dispatch, channelId);

        assertThat(advisories).anyMatch(a -> a.contains("agent-b") && a.contains("obligor"));
    }

    @Test
    void delegatedToSenderResolvingObligation_noAdvisory() {
        String corrId = UUID.randomUUID().toString();
        when(commitmentStore.findByCorrelationId(corrId)).thenReturn(Optional.of(
                Commitment.builder()
                          .id(UUID.randomUUID()).correlationId(corrId).channelId(channelId)
                          .messageType(MessageType.COMMAND).requester("requester").obligor("agent-a")
                          .delegatedTo("agent-b").state(CommitmentState.OPEN).build()));

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-b").type(MessageType.DONE)
                                                  .content("done").correlationId(corrId).inReplyTo(1L)
                                                  .actorType(ActorType.AGENT).build();

        assertThat(checker.check(dispatch, channelId)).isEmpty();
    }

    @Test
    void nullObligor_skipIdentityCheck() {
        String corrId = UUID.randomUUID().toString();
        when(commitmentStore.findByCorrelationId(corrId)).thenReturn(Optional.of(
                Commitment.builder()
                          .id(UUID.randomUUID()).correlationId(corrId).channelId(channelId)
                          .messageType(MessageType.QUERY).requester("requester")
                          .state(CommitmentState.OPEN).build()));

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("anyone").type(MessageType.RESPONSE)
                                                  .content("answer").correlationId(corrId).inReplyTo(1L)
                                                  .actorType(ActorType.AGENT).build();

        assertThat(checker.check(dispatch, channelId)).isEmpty();
    }

    // --- Cross-channel resolution ---

    @Test
    void crossChannelResolution_advisory() {
        UUID   otherChannel = UUID.randomUUID();
        String corrId       = UUID.randomUUID().toString();
        when(commitmentStore.findByCorrelationId(corrId)).thenReturn(Optional.of(
                Commitment.builder()
                          .id(UUID.randomUUID()).correlationId(corrId).channelId(otherChannel)
                          .messageType(MessageType.COMMAND).requester("requester").obligor("agent-a")
                          .state(CommitmentState.OPEN).build()));

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-a").type(MessageType.DONE)
                                                  .content("done").correlationId(corrId).inReplyTo(1L)
                                                  .actorType(ActorType.AGENT).build();

        List<String> advisories = checker.check(dispatch, channelId);

        assertThat(advisories).anyMatch(a -> a.contains("different channel"));
    }

    // --- Edge cases ---

    @Test
    void noCommitmentFound_noAdvisory() {
        when(commitmentStore.findByCorrelationId("nonexistent")).thenReturn(Optional.empty());

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-a").type(MessageType.DONE)
                                                  .content("done").correlationId("nonexistent").inReplyTo(1L)
                                                  .actorType(ActorType.AGENT).build();

        assertThat(checker.check(dispatch, channelId)).isEmpty();
    }

    @Test
    void nonTerminalType_noObligationChecks() {
        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-a").type(MessageType.STATUS)
                                                  .content("working").correlationId("some-corr").actorType(ActorType.AGENT).build();

        assertThat(checker.check(dispatch, channelId)).isEmpty();
        verifyNoInteractions(commitmentStore);
    }

    @Test
    void multipleViolations_allReported() {
        UUID   otherChannel = UUID.randomUUID();
        String corrId       = UUID.randomUUID().toString();
        when(commitmentStore.findByCorrelationId(corrId)).thenReturn(Optional.of(
                Commitment.builder()
                          .id(UUID.randomUUID()).correlationId(corrId).channelId(otherChannel)
                          .messageType(MessageType.COMMAND).requester("requester").obligor("agent-a")
                          .state(CommitmentState.OPEN).build()));
        when(messageStore.find(999L)).thenReturn(Optional.empty());

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-b").type(MessageType.RESPONSE)
                                                  .content("answer").correlationId(corrId).inReplyTo(999L)
                                                  .actorType(ActorType.AGENT).build();

        List<String> advisories = checker.check(dispatch, channelId);

        // inReplyTo not found + RESPONSE on COMMAND + wrong obligor + cross-channel
        assertThat(advisories).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void handoff_wrongSender_advisory() {
        String corrId = UUID.randomUUID().toString();
        when(commitmentStore.findByCorrelationId(corrId)).thenReturn(Optional.of(
                Commitment.builder()
                          .id(UUID.randomUUID()).correlationId(corrId).channelId(channelId)
                          .messageType(MessageType.COMMAND).requester("requester").obligor("agent-a")
                          .state(CommitmentState.OPEN).build()));

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(channelId).sender("agent-b").type(MessageType.HANDOFF)
                                                  .content("delegating").correlationId(corrId).inReplyTo(1L)
                                                  .target("agent-c").actorType(ActorType.AGENT).build();

        List<String> advisories = checker.check(dispatch, channelId);

        assertThat(advisories).anyMatch(a -> a.contains("agent-b") && a.contains("obligor"));
    }
}
