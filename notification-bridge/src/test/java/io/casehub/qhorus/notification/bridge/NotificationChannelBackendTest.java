package io.casehub.qhorus.notification.bridge;

import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceRegistry;
import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.channel.MemberRole;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.ChannelMembershipStore;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationChannelBackendTest {

    private NotificationChannelBackend backend;
    private ChannelMembershipStore membershipStore;
    @SuppressWarnings("unchecked")
    private final DataSource<Object> dataSource = mock(DataSource.class);

    @BeforeEach
    void setUp() {
        membershipStore = mock(ChannelMembershipStore.class);
        DataSourceRegistry dataSourceRegistry = mock(DataSourceRegistry.class);
        when(dataSourceRegistry.resolveSource(any(), any())).thenReturn(Optional.of(dataSource));
        backend = new NotificationChannelBackend(membershipStore, dataSourceRegistry);
    }

    @Test
    void post_firesEventPerMember() {
        UUID channelId = UUID.randomUUID();
        ChannelRef ref = new ChannelRef(channelId, "broadcast/analyzer");

        when(membershipStore.findByChannel(channelId)).thenReturn(List.of(
                membership(channelId, "agent-2"),
                membership(channelId, "agent-3")));

        OutboundMessage msg = new OutboundMessage(UUID.randomUUID(), "agent-1", MessageType.STATUS,
                "anomaly detected", null, null, null, null, null, null);

        backend.post(ref, msg);

        ArgumentCaptor<QhorusBroadcastEvent> captor = ArgumentCaptor.forClass(QhorusBroadcastEvent.class);
        verify(dataSource, times(2)).add(captor.capture());

        List<QhorusBroadcastEvent> events = captor.getAllValues();
        assertThat(events).extracting(QhorusBroadcastEvent::recipientId)
                .containsExactlyInAnyOrder("agent-2", "agent-3");
        assertThat(events).allSatisfy(e -> {
            assertThat(e.capabilityTag()).isEqualTo("analyzer");
            assertThat(e.senderId()).isEqualTo("agent-1");
            assertThat(e.content()).isEqualTo("anomaly detected");
        });
    }

    @Test
    void post_skipsSender() {
        UUID channelId = UUID.randomUUID();
        ChannelRef ref = new ChannelRef(channelId, "broadcast/analyzer");

        when(membershipStore.findByChannel(channelId)).thenReturn(List.of(
                membership(channelId, "agent-1"),
                membership(channelId, "agent-2")));

        OutboundMessage msg = new OutboundMessage(UUID.randomUUID(), "agent-1", MessageType.STATUS,
                "signal", null, null, null, null, null, null);

        backend.post(ref, msg);

        ArgumentCaptor<QhorusBroadcastEvent> captor = ArgumentCaptor.forClass(QhorusBroadcastEvent.class);
        verify(dataSource, times(1)).add(captor.capture());
        assertThat(captor.getValue().recipientId()).isEqualTo("agent-2");
    }

    @Test
    void post_nonBroadcastChannel_skips() {
        UUID channelId = UUID.randomUUID();
        ChannelRef ref = new ChannelRef(channelId, "regular-channel");

        OutboundMessage msg = new OutboundMessage(UUID.randomUUID(), "agent-1", MessageType.STATUS,
                "content", null, null, null, null, null, null);

        backend.post(ref, msg);

        verifyNoInteractions(membershipStore);
        verifyNoInteractions(dataSource);
    }

    @Test
    void backendId_isPlatformNotifications() {
        assertThat(backend.backendId()).isEqualTo("platform-notifications");
    }

    private ChannelMembership membership(UUID channelId, String memberId) {
        return new ChannelMembership(1L, channelId, memberId,
                MemberRole.PARTICIPANT, "default", Instant.now(), null, null);
    }
}
