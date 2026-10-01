package io.casehub.qhorus.connector.backend.core;

import io.casehub.connectors.ConnectorMeshBridge;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.MessageService;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectorQhorusMeshBridgeCore implements ConnectorMeshBridge {

    private static final Logger LOG = Logger.getLogger(ConnectorQhorusMeshBridgeCore.class.getName());

    private final ChannelService channelService;
    private final MessageService messageService;
    private final CurrentPrincipal currentPrincipal;
    private final Executor executor;
    private final String deliveryChannelName;

    private final ConcurrentHashMap<String, UUID> channelIdCache = new ConcurrentHashMap<>();

    public ConnectorQhorusMeshBridgeCore(ChannelService channelService, MessageService messageService,
                                          CurrentPrincipal currentPrincipal, Executor executor,
                                          String deliveryChannelName) {
        this.channelService = channelService;
        this.messageService = messageService;
        this.currentPrincipal = currentPrincipal;
        this.executor = executor;
        this.deliveryChannelName = deliveryChannelName;
    }

    @Override
    public void notifyDelivered(String connectorId, String destination, String content) {
        try {
            if (deliveryChannelName == null || deliveryChannelName.isBlank()) { return; }
            if (connectorId == null) {
                LOG.warning("ConnectorMeshBridge: connectorId is null — no-op");
                return;
            }

            String tenancyId = currentPrincipal.tenancyId();

            UUID channelId = channelIdCache.computeIfAbsent(tenancyId, tid ->
                    channelService.findByName(deliveryChannelName)
                            .map(ch -> ch.id())
                            .orElse(null));

            if (channelId == null) {
                LOG.log(Level.WARNING, "ConnectorMeshBridge: delivery-channel ''{0}'' not found for tenancy ''{1}'' — no-op",
                        new Object[]{deliveryChannelName, tenancyId});
                return;
            }

            String text = "Delivered via %s: %s".formatted(connectorId, content != null ? content : "");
            String sender = "system:connector:" + connectorId;

            executor.execute(() -> {
                try {
                    messageService.dispatch(MessageDispatch.builder()
                            .channelId(channelId)
                            .sender(sender)
                            .type(MessageType.STATUS)
                            .content(text)
                            .actorType(ActorType.SYSTEM)
                            .tenancyId(tenancyId)
                            .build());
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "ConnectorMeshBridge: dispatch failed for channel ''{0}''",
                            deliveryChannelName);
                }
            });
        } catch (Exception e) {
            LOG.log(Level.WARNING, "ConnectorMeshBridge: setup failed — connector delivery still succeeded");
        }
    }

    public void clearCache() {
        channelIdCache.clear();
    }
}
