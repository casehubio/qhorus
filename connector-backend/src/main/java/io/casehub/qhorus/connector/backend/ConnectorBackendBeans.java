package io.casehub.qhorus.connector.backend;

import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

import io.casehub.connectors.ConnectorService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.connector.backend.core.AutoChannelPolicy;
import io.casehub.qhorus.connector.backend.core.ConfiguredAutoChannelPolicyCore;
import io.casehub.qhorus.connector.backend.core.ConnectorChannelBackendCore;
import io.casehub.qhorus.connector.backend.core.ConnectorNormaliser;
import io.casehub.qhorus.connector.backend.core.ConnectorQhorusMeshBridgeCore;
import io.casehub.qhorus.api.store.ChannelBindingStore;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.qhorus.runtime.message.MessageService;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.arc.DefaultBean;
import org.eclipse.microprofile.context.ManagedExecutor;

@ApplicationScoped
public class ConnectorBackendBeans {

    @Produces
    @DefaultBean
    @ApplicationScoped
    @SuppressWarnings("unchecked")
    public AutoChannelPolicy autoChannelPolicy(ConnectorAutoChannelConfig config) {
        return new ConfiguredAutoChannelPolicyCore(() -> (Map) config.entries());
    }

    @Produces
    @ApplicationScoped
    public ConnectorChannelBackendCore connectorChannelBackend(
            ChannelGateway gateway, ChannelService channelService,
            ChannelBindingStore bindingStore, ConnectorService connectorService,
            MeterRegistry meterRegistry, AutoChannelPolicy autoChannelPolicy,
            @Any Instance<ConnectorNormaliser> connectorNormalisers) {
        Map<String, ConnectorNormaliser> normaliserMap = new HashMap<>();
        for (ConnectorNormaliser cn : connectorNormalisers) {
            String id = cn.connectorId();
            if (id == null || id.isBlank()) {
                throw new IllegalStateException(
                        cn.getClass().getName() + ".connectorId() returned null or blank");
            }
            if (normaliserMap.put(id, cn) != null) {
                throw new IllegalStateException(
                        "Duplicate ConnectorNormaliser for connectorId '" + id + "'");
            }
        }
        return new ConnectorChannelBackendCore(gateway, channelService, bindingStore,
                connectorService, meterRegistry, autoChannelPolicy, normaliserMap);
    }

    @Produces
    @ApplicationScoped
    public ConnectorQhorusMeshBridgeCore connectorMeshBridge(
            ChannelService channelService, MessageService messageService,
            CurrentPrincipal currentPrincipal, ManagedExecutor executor,
            QhorusConfig config) {
        String deliveryChannelName = config.connectorBackend().deliveryChannel().orElse("");
        return new ConnectorQhorusMeshBridgeCore(channelService, messageService,
                currentPrincipal, executor, deliveryChannelName);
    }
}
