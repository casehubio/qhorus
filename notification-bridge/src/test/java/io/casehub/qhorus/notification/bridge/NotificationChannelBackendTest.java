package io.casehub.qhorus.notification.bridge;

import io.casehub.platform.api.subscription.SubscribableEvent;
import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.channel.MemberRole;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.ChannelMembershipStore;
import io.casehub.qhorus.notification.bridge.core.NotificationChannelBackendCore;
import io.casehub.qhorus.notification.bridge.core.QhorusBroadcastEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationChannelBackendTest {

    private NotificationChannelBackendCore backend;
    private ChannelMembershipStore membershipStore;
    @SuppressWarnings("unchecked")
    private final Consumer<SubscribableEvent> eventSink = mock(Consumer.class);

    @BeforeEach
    void setUp() {
        membershipStore = mock(ChannelMembershipStore.class);
        backend = new NotificationChannelBackendCore(membershipStore, eventSink);
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
        verify(eventSink, times(2)).accept(captor.capture());

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

        ArgumentCaptor<SubscribableEvent> captor = ArgumentCaptor.forClass(SubscribableEvent.class);
        verify(eventSink, times(1)).accept(captor.capture());
        assertThat(((QhorusBroadcastEvent) captor.getValue()).recipientId()).isEqualTo("agent-2");
    }

    @Test
    void post_nonBroadcastChannel_skips() {
        UUID channelId = UUID.randomUUID();
        ChannelRef ref = new ChannelRef(channelId, "regular-channel");

        OutboundMessage msg = new OutboundMessage(UUID.randomUUID(), "agent-1", MessageType.STATUS,
                "content", null, null, null, null, null, null);

        backend.post(ref, msg);

        verifyNoInteractions(membershipStore);
        verifyNoInteractions(eventSink);
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
