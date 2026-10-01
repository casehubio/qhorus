package io.casehub.qhorus.connector.backend;

import io.casehub.qhorus.connector.backend.core.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.InboundConnectorIds;
import io.casehub.qhorus.api.gateway.*;
import io.casehub.qhorus.api.channel.ChannelConnectorBinding;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.connectors.ConnectorService;
import io.casehub.qhorus.api.store.ChannelBindingStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ConnectorNormaliserDispatchTest {

    private ConnectorChannelBackendCore backend;
    private ChannelBindingStore bindingStore;

    @BeforeEach
    void setUp() {
        ChannelGateway gateway = mock(ChannelGateway.class);
        ChannelService channelService = mock(ChannelService.class);
        bindingStore = mock(ChannelBindingStore.class);
        ConnectorService connectorService = mock(ConnectorService.class);
        AutoChannelPolicy autoChannelPolicy = mock(AutoChannelPolicy.class);
        backend = new ConnectorChannelBackendCore(gateway, channelService, bindingStore,
                connectorService, new SimpleMeterRegistry(), autoChannelPolicy, java.util.Map.of());
    }

    @Test
    void normaliserFor_returnsNull_whenNoCacheEntry() {
        assertThat(backend.normaliserFor(UUID.randomUUID())).isNull();
    }

    @Test
    void normaliserFor_returnsNull_whenNoConnectorNormaliserRegistered() {
        UUID channelId = UUID.randomUUID();
        ChannelConnectorBinding b = new ChannelConnectorBinding(channelId, InboundConnectorIds.TWILIO_SMS, "+1111", "twilio-sms", "+9999");
        when(bindingStore.findByChannelId(channelId)).thenReturn(Optional.of(b));
        backend.onChannelInitialised(channelId);

        assertThat(backend.normaliserFor(channelId)).isNull();
    }
}
