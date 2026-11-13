package io.casehub.qhorus.graphql.channels;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.gateway.ChannelBackend;
import io.casehub.qhorus.api.gateway.BackendRegistration;
import io.casehub.qhorus.api.channel.BackendInfo;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.inject.Instance;
import java.util.stream.Stream;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.MembershipManager;
import io.casehub.qhorus.api.channel.SpaceManager;
import io.casehub.qhorus.api.channel.TopicManager;
import io.casehub.qhorus.api.channel.UnreadCount;
import io.casehub.qhorus.api.channel.UnreadCountProvider;
import io.casehub.qhorus.api.gateway.BackendRegistry;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.MessageReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelsServiceMembershipTest {

    private ChannelsService service;
    private MembershipManager membershipManager;
    private UnreadCountProvider unreadCountProvider;
    private ChannelReader channelReader;
    private io.casehub.qhorus.api.store.MessageStore messageStore;
    private CurrentPrincipal currentPrincipal;
    private BackendRegistry backendRegistry;

    @BeforeEach
    void setUp() {
        membershipManager = mock(MembershipManager.class);
        unreadCountProvider = mock(UnreadCountProvider.class);
        channelReader = mock(ChannelReader.class);
        messageStore = mock(io.casehub.qhorus.api.store.MessageStore.class);
        currentPrincipal = mock(CurrentPrincipal.class);
        backendRegistry = mock(BackendRegistry.class);
        service = new ChannelsService(
                channelReader, mock(ConsumerMessaging.class),
                mock(ChannelManager.class), mock(TopicManager.class),
                membershipManager, unreadCountProvider,
                mock(SpaceManager.class), backendRegistry,
                messageStore, currentPrincipal,
                mock(io.casehub.qhorus.api.channel.ChannelSummaryManager.class),
                mock(io.casehub.qhorus.api.channel.ProjectionReader.class),
                mock(io.casehub.qhorus.api.channel.ProtocolReader.class),
                mock(io.casehub.qhorus.api.channel.RoutingDiagnostics.class),
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void membersDelegatesToManager() {
        UUID channelId = UUID.randomUUID();
        var membership = new ChannelMembership(1L, channelId, "agent-1",
                io.casehub.qhorus.api.channel.MemberRole.PARTICIPANT, "t", Instant.now(), 0L);
        when(membershipManager.listMembers(channelId)).thenReturn(List.of(membership));

        var result = service.members(channelId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).memberId()).isEqualTo("agent-1");
    }

    @Test
    void unreadCountsDelegatesToProvider() {
        when(currentPrincipal.tenancyId()).thenReturn("test-tenant");
        UUID channelId = UUID.randomUUID();
        var unread = new UnreadCount(channelId, "ch1", 5, 100L);
        when(unreadCountProvider.getUnreadCounts("agent-1", "test-tenant"))
                .thenReturn(Map.of(channelId, unread));

        var result = service.unreadCounts("agent-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).count()).isEqualTo(5);
    }

    @Test
    void messageDeliveryStatusThrowsWhenTrackingDisabled() {
        UUID channelId = UUID.randomUUID();
        Channel ch = Channel.builder("test-ch")
                .id(channelId)
                .semantic(ChannelSemantic.APPEND)
                .trackDelivery(false)
                .tenancyId("t")
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();
        when(channelReader.findById(channelId)).thenReturn(Optional.of(ch));

        assertThatThrownBy(() -> service.messageDeliveryStatus(channelId, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void joinChannelDelegatesToManager() {
        UUID channelId = UUID.randomUUID();
        var membership = new ChannelMembership(1L, channelId, "agent-1",
                io.casehub.qhorus.api.channel.MemberRole.PARTICIPANT, "t", Instant.now(), 0L);
        when(membershipManager.join(channelId, "agent-1")).thenReturn(membership);

        var result = service.joinChannel(channelId, "agent-1", null);

        assertThat(result.memberId()).isEqualTo("agent-1");
    }

    @Test
    void leaveChannelDelegatesToManager() {
        UUID channelId = UUID.randomUUID();
        var membership = new ChannelMembership(1L, channelId, "agent-1",
                io.casehub.qhorus.api.channel.MemberRole.PARTICIPANT, "t", Instant.now(), 0L);
        when(membershipManager.listMembers(channelId)).thenReturn(List.of(membership));

        assertThat(service.leaveChannel(channelId, "agent-1")).isEqualTo(membership);

        verify(membershipManager).leave(channelId, "agent-1");
    }

    @Test
    void markChannelReadDelegatesToManager() {
        UUID channelId = UUID.randomUUID();
        when(currentPrincipal.tenancyId()).thenReturn("test-tenant");
        var unread = new UnreadCount(channelId, "ch1", 0, 50L);
        when(unreadCountProvider.getUnreadCounts("agent-1", "test-tenant"))
                .thenReturn(Map.of(channelId, unread));

        assertThat(service.markChannelRead(channelId, "agent-1", 50L)).isEqualTo(unread);

        verify(membershipManager).markRead(channelId, "agent-1", 50L);
    }

    @Test
    void markChannelReadUsesLatestMessageWhenNull() {
        UUID channelId = UUID.randomUUID();
        var msg = Message.builder()
                .id(99L)
                .channelId(channelId)
                .sender("s")
                .messageType(MessageType.STATUS)
                .content("c")
                .createdAt(Instant.now())
                .build();
        when(messageStore.findLastMessage(channelId)).thenReturn(Optional.of(msg));

        service.markChannelRead(channelId, "agent-1", null);

        verify(membershipManager).markRead(channelId, "agent-1", 99L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void registerBackendResolvesImplementationAndReturnsInfo() {
        UUID channelId = UUID.randomUUID();
        var backend = mock(ChannelBackend.class);
        when(backend.backendId()).thenReturn("external");
        when(backend.actorType()).thenReturn(ActorType.HUMAN);
        service.availableBackends = mock(Instance.class);
        when(service.availableBackends.stream()).thenReturn(Stream.of(backend));

        var result = service.registerBackend(channelId, "external", "human_participating");

        assertThat(result).isEqualTo(new BackendInfo(
                "external", "human_participating", "human"));
        verify(backendRegistry).registerBackend(channelId, backend, "human_participating");
    }

    @Test
    @SuppressWarnings("unchecked")
    void registerBackendRejectsUnknownImplementation() {
        service.availableBackends = mock(Instance.class);
        when(service.availableBackends.stream()).thenReturn(Stream.empty());

        assertThatThrownBy(() -> service.registerBackend(UUID.randomUUID(), "missing", "external"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Backend not found: missing");
        org.mockito.Mockito.verifyNoInteractions(backendRegistry);
    }

    @Test
    void deregisterBackendReturnsRemovedInfo() {
        UUID channelId = UUID.randomUUID();
        when(backendRegistry.listBackends(channelId)).thenReturn(List.of(
                new BackendRegistration("external", "agent",
                        ActorType.AGENT)));

        assertThat(service.deregisterBackend(channelId, "external"))
                .isEqualTo(new BackendInfo("external", "agent", "agent"));
        verify(backendRegistry).deregisterBackend(channelId, "external");
    }
}
