package io.casehub.qhorus.runtime.broadcast;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.FindOrCreateResult;
import io.casehub.qhorus.api.channel.MemberRole;
import io.casehub.qhorus.api.instance.InstanceDeregisteredEvent;
import io.casehub.qhorus.api.instance.InstanceRegisteredEvent;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.ChannelMembershipService;
import io.casehub.qhorus.runtime.channel.ChannelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BroadcastMembershipManagerTest {

    private BroadcastMembershipManager manager;
    private ChannelService channelService;
    private ChannelMembershipService membershipService;

    @BeforeEach
    void setUp() {
        channelService = mock(ChannelService.class);
        membershipService = mock(ChannelMembershipService.class);
        manager = new BroadcastMembershipManager(channelService, membershipService);
    }

    @Test
    void onRegistered_createsChannelAndJoins() {
        UUID channelId = UUID.randomUUID();
        Channel channel = stubChannel(channelId, "broadcast/analyzer");
        when(channelService.findOrCreate(any())).thenReturn(new FindOrCreateResult(channel, true));
        when(membershipService.join(any(), any(), any(), any()))
                .thenReturn(stubMembership(channelId, "agent-1"));

        manager.onRegistered(new InstanceRegisteredEvent("agent-1", List.of(), List.of("analyzer")));

        ArgumentCaptor<ChannelCreateRequest> reqCaptor = ArgumentCaptor.forClass(ChannelCreateRequest.class);
        verify(channelService).findOrCreate(reqCaptor.capture());
        ChannelCreateRequest req = reqCaptor.getValue();
        assertThat(req.name()).isEqualTo("broadcast/analyzer");
        assertThat(req.semantic()).isEqualTo(ChannelSemantic.BROADCAST);
        assertThat(req.allowedTypes()).isEqualTo(Set.of(MessageType.STATUS, MessageType.EVENT));
        verify(membershipService).join(eq(channelId), eq("agent-1"), eq(MemberRole.PARTICIPANT), any());
    }

    @Test
    void onRegistered_removedCapability_leavesChannel() {
        UUID channelId = UUID.randomUUID();
        Channel channel = stubChannel(channelId, "broadcast/analyzer");
        when(channelService.findByName("broadcast/analyzer")).thenReturn(Optional.of(channel));

        manager.onRegistered(new InstanceRegisteredEvent(
                "agent-1", List.of("analyzer", "monitor"), List.of("monitor")));

        verify(membershipService).leave(channelId, "agent-1");
        verify(channelService, never()).findOrCreate(
                argThat(req -> req.name().equals("broadcast/analyzer")));
    }

    @Test
    void onRegistered_unchangedCapability_noop() {
        manager.onRegistered(new InstanceRegisteredEvent(
                "agent-1", List.of("analyzer"), List.of("analyzer")));

        verifyNoInteractions(channelService);
        verifyNoInteractions(membershipService);
    }

    @Test
    void onDeregistered_leavesAllBroadcastChannels() {
        UUID channelId = UUID.randomUUID();
        Channel channel = stubChannel(channelId, "broadcast/analyzer");
        when(channelService.findByName("broadcast/analyzer")).thenReturn(Optional.of(channel));

        manager.onDeregistered(new InstanceDeregisteredEvent("agent-1", List.of("analyzer")));

        verify(membershipService).leave(channelId, "agent-1");
    }

    @Test
    void onRegistered_multipleAddedCapabilities_joinsAll() {
        UUID ch1 = UUID.randomUUID();
        UUID ch2 = UUID.randomUUID();
        when(channelService.findOrCreate(argThat(r -> r != null && r.name().equals("broadcast/analyzer"))))
                .thenReturn(new FindOrCreateResult(stubChannel(ch1, "broadcast/analyzer"), true));
        when(channelService.findOrCreate(argThat(r -> r != null && r.name().equals("broadcast/monitor"))))
                .thenReturn(new FindOrCreateResult(stubChannel(ch2, "broadcast/monitor"), true));
        when(membershipService.join(any(), any(), any(), any()))
                .thenReturn(stubMembership(ch1, "agent-1"));

        manager.onRegistered(new InstanceRegisteredEvent(
                "agent-1", List.of(), List.of("analyzer", "monitor")));

        verify(membershipService).join(eq(ch1), eq("agent-1"), any(), any());
        verify(membershipService).join(eq(ch2), eq("agent-1"), any(), any());
    }

    private Channel stubChannel(UUID id, String name) {
        return Channel.builder(name).id(id).semantic(ChannelSemantic.BROADCAST)
                .tenancyId("default").createdAt(Instant.now()).lastActivityAt(Instant.now()).build();
    }

    private ChannelMembership stubMembership(UUID channelId, String memberId) {
        return new ChannelMembership(1L, channelId, memberId,
                MemberRole.PARTICIPANT, "default", Instant.now(), null, null);
    }
}
