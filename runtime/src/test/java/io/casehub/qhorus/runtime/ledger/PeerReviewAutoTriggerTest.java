package io.casehub.qhorus.runtime.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PeerReviewAutoTriggerTest {

    private PeerReviewAutoTrigger trigger;
    private MessageLedgerEntryRepository messageRepo;
    private ReviewerResolver reviewerResolver;
    private MessageDispatcher messageDispatcher;
    private static final String TENANT = "test-tenant";

    @BeforeEach
    void setUp() {
        trigger = new PeerReviewAutoTrigger();
        messageRepo = mock(MessageLedgerEntryRepository.class);
        reviewerResolver = mock(ReviewerResolver.class);
        messageDispatcher = mock(MessageDispatcher.class);
        trigger.messageRepo = messageRepo;
        trigger.reviewerResolver = reviewerResolver;
        trigger.messageDispatcher = messageDispatcher;
        trigger.objectMapper = new ObjectMapper();
    }

    @Test
    void fires_review_query_after_done() {
        UUID channelId = UUID.randomUUID();
        String corrId = UUID.randomUUID().toString();
        MessageLedgerEntry entry = commandEntry("requester", channelId);
        when(messageRepo.findEarliestWithSubjectByCorrelationId(corrId, TENANT))
                .thenReturn(Optional.of(entry));
        when(reviewerResolver.resolve(eq(entry.channelId), anyList(), eq(entry.id), eq(TENANT)))
                .thenReturn(List.of("reviewer-1"));
        when(messageDispatcher.dispatch(any())).thenReturn(null);

        trigger.onMessage(doneEvent(channelId, corrId, "work completed"));

        ArgumentCaptor<MessageDispatch> captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        MessageDispatch dispatch = captor.getValue();
        assertThat(dispatch.type()).isEqualTo(MessageType.QUERY);
        assertThat(dispatch.target()).isEqualTo("reviewer-1");
        assertThat(dispatch.sender()).isEqualTo("requester");
        assertThat(dispatch.content()).contains("peer_review");
        assertThat(dispatch.content()).contains("ledger_entry_id");
    }

    @Test
    void ignores_non_done_messages() {
        trigger.onMessage(event(MessageType.STATUS, UUID.randomUUID(), "corr", "content"));
        verify(messageRepo, never()).findEarliestWithSubjectByCorrelationId(any(), any());
    }

    @Test
    void ignores_done_with_null_correlation_id() {
        trigger.onMessage(event(MessageType.DONE, UUID.randomUUID(), null, "content"));
        verify(messageRepo, never()).findEarliestWithSubjectByCorrelationId(any(), any());
    }

    @Test
    void ignores_when_entry_is_not_command() {
        String corrId = UUID.randomUUID().toString();
        MessageLedgerEntry entry = commandEntry("agent", UUID.randomUUID());
        entry.messageType = "QUERY";
        when(messageRepo.findEarliestWithSubjectByCorrelationId(corrId, TENANT))
                .thenReturn(Optional.of(entry));

        trigger.onMessage(doneEvent(UUID.randomUUID(), corrId, "done"));
        verify(reviewerResolver, never()).resolve(any(), anyList(), any(), any());
    }

    @Test
    void noop_when_no_reviewers_found() {
        String corrId = UUID.randomUUID().toString();
        MessageLedgerEntry entry = commandEntry("requester", UUID.randomUUID());
        when(messageRepo.findEarliestWithSubjectByCorrelationId(corrId, TENANT))
                .thenReturn(Optional.of(entry));
        when(reviewerResolver.resolve(any(), anyList(), any(), any()))
                .thenReturn(List.of());

        trigger.onMessage(doneEvent(UUID.randomUUID(), corrId, "done"));
        verify(messageDispatcher, never()).dispatch(any());
    }

    @Test
    void each_review_query_gets_own_correlation_id() {
        String corrId = UUID.randomUUID().toString();
        MessageLedgerEntry entry = commandEntry("requester", UUID.randomUUID());
        when(messageRepo.findEarliestWithSubjectByCorrelationId(corrId, TENANT))
                .thenReturn(Optional.of(entry));
        when(reviewerResolver.resolve(any(), anyList(), any(), any()))
                .thenReturn(List.of("rev-1", "rev-2"));
        when(messageDispatcher.dispatch(any())).thenReturn(null);

        trigger.onMessage(doneEvent(UUID.randomUUID(), corrId, "done"));

        ArgumentCaptor<MessageDispatch> captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher, org.mockito.Mockito.times(2)).dispatch(captor.capture());
        List<MessageDispatch> dispatches = captor.getAllValues();
        assertThat(dispatches.get(0).correlationId())
                .isNotEqualTo(dispatches.get(1).correlationId());
    }

    @Test
    void query_content_includes_completion_content() {
        String corrId = UUID.randomUUID().toString();
        MessageLedgerEntry entry = commandEntry("requester", UUID.randomUUID());
        entry.content = "do the task";
        when(messageRepo.findEarliestWithSubjectByCorrelationId(corrId, TENANT))
                .thenReturn(Optional.of(entry));
        when(reviewerResolver.resolve(any(), anyList(), any(), any()))
                .thenReturn(List.of("rev-1"));
        when(messageDispatcher.dispatch(any())).thenReturn(null);

        trigger.onMessage(doneEvent(UUID.randomUUID(), corrId, "task completed"));

        ArgumentCaptor<MessageDispatch> captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().content()).contains("task completed");
        assertThat(captor.getValue().content()).contains("do the task");
    }

    private MessageLedgerEntry commandEntry(String actorId, UUID channelId) {
        MessageLedgerEntry e = new MessageLedgerEntry();
        e.id = UUID.randomUUID();
        e.subjectId = UUID.randomUUID();
        e.channelId = channelId;
        e.messageId = 1L;
        e.messageType = "COMMAND";
        e.actorId = actorId;
        e.actorType = ActorType.AGENT;
        e.occurredAt = Instant.now();
        e.content = "{\"task\": \"test\"}";
        return e;
    }

    private MessageReceivedEvent doneEvent(UUID channelId, String corrId, String content) {
        return event(MessageType.DONE, channelId, corrId, content);
    }

    private MessageReceivedEvent event(MessageType type, UUID channelId,
                                       String corrId, String content) {
        return new MessageReceivedEvent(1L, "test-ch", channelId, TENANT,
                type, "obligor", corrId, Instant.now(), content, null);
    }
}
