package io.casehub.qhorus.runtime.capacity;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.runtime.channel.ChannelSummaryService;
import io.casehub.qhorus.runtime.message.MessageService;
import io.casehub.qhorus.runtime.message.RoutingBridge;
import io.casehub.platform.api.capacity.RedistributionDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedistributionDelegateTest {

    private MessageService messageService;
    private RoutingBridge routingBridge;
    private CrossTenantChannelStore channelStore;
    private MessageStore messageStore;
    private RedistributionDelegate delegate;

    @BeforeEach
    void setUp() {
        messageService = mock(MessageService.class);
        routingBridge = mock(RoutingBridge.class);
        channelStore = mock(CrossTenantChannelStore.class);
        messageStore = mock(MessageStore.class);
        delegate = new RedistributionDelegate(
                mock(ChannelSummaryService.class),
                messageService,
                routingBridge,
                channelStore,
                messageStore,
                tenancyId -> {},
                event -> {},
                0.85);
    }

    @Test
    void redistribute_passesRoleTargetToRoutingBridge() {
        var channelId = UUID.randomUUID();
        var correlationId = UUID.randomUUID().toString();
        var channel = Channel.builder("test-ch").id(channelId).build();
        var obligation = Commitment.builder()
                .id(UUID.randomUUID()).correlationId(correlationId)
                .channelId(channelId).messageType(MessageType.COMMAND)
                .requester("requester-1").obligor("agent-1")
                .state(CommitmentState.OPEN).tenancyId("tenant-1")
                .capabilityTag("analyst").build();

        var originalMsg = mock(io.casehub.qhorus.api.message.Message.class);
        when(originalMsg.id()).thenReturn(42L);
        when(messageStore.scan(any())).thenReturn(List.of(originalMsg));
        when(channelStore.findById(channelId)).thenReturn(Optional.of(channel));

        var routingOutcome = new RoutingBridge.RoutingOutcome(
                "team-member-1", "role:analyst", "eidos-org-preferred", 2, 0.8);
        when(routingBridge.resolve(any(), eq(channel), eq("tenant-1")))
                .thenReturn(routingOutcome);

        delegate.redistribute("agent-1", List.of(obligation),
                new RedistributionDecision.Redistribute("high pressure", java.time.Duration.ZERO, Set.of("agent-1")),
                0.9);

        var dispatchCaptor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(routingBridge).resolve(dispatchCaptor.capture(), eq(channel), eq("tenant-1"));
        assertThat(dispatchCaptor.getValue().target()).isEqualTo("role:analyst");
    }
}
