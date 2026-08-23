package io.casehub.qhorus.runtime.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.EnforcementMode;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.EnforcementBlockedEvent;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.CommitmentService;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EnforcementExecutorTest {

    EnforcementExecutor executor;
    MessageDispatcher messageDispatcher;
    ChannelService channelService;
    CommitmentService commitmentService;
    Event<EnforcementBlockedEvent> enforcementEvent;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        messageDispatcher = mock(MessageDispatcher.class);
        channelService = mock(ChannelService.class);
        commitmentService = mock(CommitmentService.class);
        enforcementEvent = mock(Event.class);
        when(messageDispatcher.dispatch(any())).thenReturn(
                new DispatchResult(1L, UUID.randomUUID(), "system:enforcement",
                        MessageType.EVENT, null, null, List.of(), null,
                        null, null, null, 0, List.of()));
        executor = new EnforcementExecutor();
        executor.messageDispatcher = messageDispatcher;
        executor.channelService = channelService;
        executor.commitmentService = commitmentService;
        executor.enforcementBlockedEvent = enforcementEvent;
        executor.objectMapper = new ObjectMapper();
    }

    private Channel channel(EnforcementMode mode) {
        return Channel.builder("test-ch")
                .id(UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND)
                .enforcementMode(mode)
                .tenancyId("default")
                .build();
    }

    @Test
    void blockingModeDispatchesEventButDoesNotPause() {
        Channel ch = channel(EnforcementMode.BLOCKING);
        List<TaggedAdvisory> violations = List.of(
                new TaggedAdvisory("REQUEST_RESPONSE", "[REQUEST_RESPONSE] too many queries"));
        MessageDispatch dispatch = MessageDispatch.builder()
                .channelId(ch.id()).sender("agent-1").type(MessageType.QUERY)
                .content("test").actorType(ActorType.AGENT).build();

        executor.execute(ch, dispatch, violations, "default");

        var captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        MessageDispatch eventDispatch = captor.getValue();
        assertThat(eventDispatch.sender()).isEqualTo("system:enforcement");
        assertThat(eventDispatch.type()).isEqualTo(MessageType.EVENT);

        verify(channelService, never()).pause(any());
        verify(commitmentService, never()).expireByChannel(any());
        verify(enforcementEvent).fireAsync(any(EnforcementBlockedEvent.class));
    }

    @Test
    void quarantineModeDispatchesEventAndPausesAndExpires() {
        Channel ch = channel(EnforcementMode.QUARANTINE);
        List<TaggedAdvisory> violations = List.of(
                new TaggedAdvisory("TYPE_POLICY", "type violation"));
        MessageDispatch dispatch = MessageDispatch.builder()
                .channelId(ch.id()).sender("agent-1").type(MessageType.STATUS)
                .content("test").actorType(ActorType.AGENT).build();

        executor.execute(ch, dispatch, violations, "default");

        verify(messageDispatcher).dispatch(any());
        verify(channelService).pause(ch.id());
        verify(commitmentService).expireByChannel(ch.id());
        verify(enforcementEvent).fireAsync(any(EnforcementBlockedEvent.class));
    }

    @Test
    void eventTelemetryContainsViolationDetails() {
        Channel ch = channel(EnforcementMode.BLOCKING);
        List<TaggedAdvisory> violations = List.of(
                new TaggedAdvisory("REQUEST_RESPONSE", "violation text"));
        MessageDispatch dispatch = MessageDispatch.builder()
                .channelId(ch.id()).sender("agent-1").type(MessageType.QUERY)
                .content("test").actorType(ActorType.AGENT).build();

        executor.execute(ch, dispatch, violations, "default");

        var captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        String telemetry = captor.getValue().telemetry();
        assertThat(telemetry).contains("BLOCKED");
        assertThat(telemetry).contains("agent-1");
        assertThat(telemetry).contains("REQUEST_RESPONSE");
    }

    @Test
    void cdiEventContainsStructuredData() {
        Channel ch = channel(EnforcementMode.BLOCKING);
        List<TaggedAdvisory> violations = List.of(
                new TaggedAdvisory("TYPE_POLICY", "v1"),
                new TaggedAdvisory("REQUEST_RESPONSE", "v2"));
        MessageDispatch dispatch = MessageDispatch.builder()
                .channelId(ch.id()).sender("agent-1").type(MessageType.COMMAND)
                .content("test").actorType(ActorType.AGENT).build();

        executor.execute(ch, dispatch, violations, "default");

        var captor = ArgumentCaptor.forClass(EnforcementBlockedEvent.class);
        verify(enforcementEvent).fireAsync(captor.capture());
        EnforcementBlockedEvent event = captor.getValue();
        assertThat(event.channelId()).isEqualTo(ch.id());
        assertThat(event.channelName()).isEqualTo("test-ch");
        assertThat(event.mode()).isEqualTo(EnforcementMode.BLOCKING);
        assertThat(event.blockedSender()).isEqualTo("agent-1");
        assertThat(event.blockedType()).isEqualTo(MessageType.COMMAND);
        assertThat(event.violations()).containsExactly("v1", "v2");
        assertThat(event.violationSources()).containsExactly("TYPE_POLICY", "REQUEST_RESPONSE");
    }
}
