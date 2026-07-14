package io.casehub.qhorus.runtime.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.gateway.ChannelBackend;
import io.casehub.qhorus.api.gateway.ChannelClosedEvent;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;
import io.casehub.qhorus.runtime.config.DeliveryConfig;
import io.casehub.qhorus.runtime.config.QhorusTracingConfig;
import io.casehub.qhorus.runtime.message.MessageService;
import io.opentelemetry.api.trace.Tracer;

class ChannelGatewayClosedEventTest {

    private final List<ChannelClosedEvent> firedEvents = new ArrayList<>();
    private ChannelGateway gateway;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        Event<ChannelClosedEvent> closedEvents = mock(Event.class);
        doAnswer(inv -> { firedEvents.add(inv.getArgument(0)); return null; })
                .when(closedEvents).fire(any());
        DeliveryConfig deliveryConfig = mock(DeliveryConfig.class);
        gateway = new ChannelGateway(
                new QhorusChannelBackend(),
                new DefaultInboundNormaliser(),
                mock(MessageService.class),
                null,
                mock(CrossTenantChannelStore.class),
                mock(Event.class),
                closedEvents,
                deliveryConfig,
                mock(io.casehub.qhorus.api.store.CrossTenantMessageStore.class),
                null, mock(Instance.class),
                mock(QhorusTracingConfig.class));
    }

    @Test
    void closeChannelFiresClosedEvent() {
        UUID channelId = UUID.randomUUID();
        gateway.initChannel(channelId, new ChannelRef(channelId, "test-ch"));

        gateway.closeChannel(channelId, new ChannelRef(channelId, "test-ch"));

        assertThat(firedEvents).hasSize(1);
        assertThat(firedEvents.get(0).channelId()).isEqualTo(channelId);
        assertThat(firedEvents.get(0).channelName()).isEqualTo("test-ch");
    }

    @Test
    void closeChannelFiresEventEvenWhenNoBackendsRegistered() {
        UUID channelId = UUID.randomUUID();

        gateway.closeChannel(channelId, new ChannelRef(channelId, "empty-ch"));

        assertThat(firedEvents).hasSize(1);
        assertThat(firedEvents.get(0).channelId()).isEqualTo(channelId);
    }

    @Test
    void closeChannelFiresEventAfterBackendsAreClosed() {
        UUID channelId = UUID.randomUUID();
        gateway.initChannel(channelId, new ChannelRef(channelId, "test-ch"));

        List<String> closeOrder = new ArrayList<>();
        gateway.registerBackend(channelId, new OrderRecordingBackend("b1", closeOrder), "agent");

        gateway.closeChannel(channelId, new ChannelRef(channelId, "test-ch"));

        assertThat(closeOrder).containsExactly("b1:closed");
        assertThat(firedEvents).hasSize(1);
    }

    private static class OrderRecordingBackend implements ChannelBackend {
        private final String id;
        private final List<String> recorder;

        OrderRecordingBackend(String id, List<String> recorder) {
            this.id = id;
            this.recorder = recorder;
        }

        @Override public String backendId() { return id; }
        @Override public ActorType actorType() { return ActorType.AGENT; }
        @Override public void open(ChannelRef channel, Map<String, String> metadata) {}
        @Override public void post(ChannelRef channel, OutboundMessage message) {}
        @Override public void close(ChannelRef channel) { recorder.add(id + ":closed"); }
    }
}
