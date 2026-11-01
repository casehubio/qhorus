package io.casehub.qhorus.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.MessageService;
import io.casehub.qhorus.runtime.message.RoutingBridge;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class MessageServiceDelegationAdvisoryTest {

    @Inject MessageService messageService;
    @Inject ChannelService channelService;
    @InjectMock RoutingBridge routingBridge;

    private record ChannelAndCommand(UUID channelId, Long commandMessageId, String correlationId) {}

    private ChannelAndCommand setupChannelWithCommand(String suffix) {
        UUID[] channelId = new UUID[1];
        Long[] messageId = new Long[1];
        String corrId = UUID.randomUUID().toString();

        QuarkusTransaction.requiringNew().run(() -> {
            Channel ch = channelService.create(ChannelCreateRequest.builder("deleg-" + suffix).build());
            channelId[0] = ch.id();
            DispatchResult cmd = messageService.dispatch(MessageDispatch.builder()
                    .channelId(ch.id())
                    .sender("agent-a")
                    .type(MessageType.COMMAND)
                    .content("do something")
                    .correlationId(corrId)
                    .target("agent-b")
                    .actorType(ActorType.AGENT)
                    .build());
            messageId[0] = cmd.messageId();
        });

        return new ChannelAndCommand(channelId[0], messageId[0], corrId);
    }

    @Test
    void handoffWithDelegationDeniedProducesAdvisory() {
        when(routingBridge.canDelegateTo(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(false));

        var setup = setupChannelWithCommand("denied-" + System.nanoTime());

        DispatchResult[] result = new DispatchResult[1];
        QuarkusTransaction.requiringNew().run(() ->
                result[0] = messageService.dispatch(MessageDispatch.builder()
                        .channelId(setup.channelId())
                        .sender("agent-a")
                        .type(MessageType.HANDOFF)
                        .content("delegating to agent-c")
                        .correlationId(setup.correlationId())
                        .inReplyTo(setup.commandMessageId())
                        .target("agent-c")
                        .actorType(ActorType.AGENT)
                        .build()));

        assertThat(result[0].advisories())
                .anyMatch(a -> "ORG_DELEGATION".equals(a.source()));
    }

    @Test
    void handoffWithDelegationAllowedProducesNoAdvisory() {
        when(routingBridge.canDelegateTo(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(true));

        var setup = setupChannelWithCommand("allowed-" + System.nanoTime());

        DispatchResult[] result = new DispatchResult[1];
        QuarkusTransaction.requiringNew().run(() ->
                result[0] = messageService.dispatch(MessageDispatch.builder()
                        .channelId(setup.channelId())
                        .sender("agent-a")
                        .type(MessageType.HANDOFF)
                        .content("delegating to agent-c")
                        .correlationId(setup.correlationId())
                        .inReplyTo(setup.commandMessageId())
                        .target("agent-c")
                        .actorType(ActorType.AGENT)
                        .build()));

        assertThat(result[0].advisories())
                .noneMatch(a -> "ORG_DELEGATION".equals(a.source()));
    }

    @Test
    void handoffFromSystemSenderSkipsDelegationCheck() {
        when(routingBridge.canDelegateTo(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(false));

        var setup = setupChannelWithCommand("system-" + System.nanoTime());

        DispatchResult[] result = new DispatchResult[1];
        QuarkusTransaction.requiringNew().run(() ->
                result[0] = messageService.dispatch(MessageDispatch.builder()
                        .channelId(setup.channelId())
                        .sender("system:watchdog")
                        .type(MessageType.HANDOFF)
                        .content("system delegating")
                        .correlationId(setup.correlationId())
                        .inReplyTo(setup.commandMessageId())
                        .target("agent-c")
                        .actorType(ActorType.SYSTEM)
                        .build()));

        assertThat(result[0].advisories())
                .noneMatch(a -> "ORG_DELEGATION".equals(a.source()));
    }

    @Test
    void nonHandoffTypeSkipsDelegationCheck() {
        when(routingBridge.canDelegateTo(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(false));

        UUID[] channelId = new UUID[1];
        QuarkusTransaction.requiringNew().run(() -> {
            Channel ch = channelService.create(
                    ChannelCreateRequest.builder("deleg-status-" + System.nanoTime()).build());
            channelId[0] = ch.id();
        });

        DispatchResult[] result = new DispatchResult[1];
        QuarkusTransaction.requiringNew().run(() ->
                result[0] = messageService.dispatch(MessageDispatch.builder()
                        .channelId(channelId[0])
                        .sender("agent-a")
                        .type(MessageType.STATUS)
                        .content("just a status")
                        .actorType(ActorType.AGENT)
                        .build()));

        assertThat(result[0].advisories())
                .noneMatch(a -> "ORG_DELEGATION".equals(a.source()));
        verify(routingBridge, never()).canDelegateTo(anyString(), anyString(), anyString());
    }
}
