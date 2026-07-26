package io.casehub.qhorus.connector.backend;

import io.casehub.connectors.ConnectorMeshBridge;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.runtime.message.MessageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ConnectorQhorusMeshBridge implements ConnectorMeshBridge {

    private static final Logger LOG = Logger.getLogger(ConnectorQhorusMeshBridge.class);

    private final ChannelService   channelService;
    private final MessageService   messageService;
    private final CurrentPrincipal currentPrincipal;
    private final ManagedExecutor  executor;
    private final String           deliveryChannelName;

    // Keyed by tenancyId — each tenant resolves to its own channel UUID for the same name.
    // computeIfAbsent does not cache null, so a missing channel is retried on every call.
    private final ConcurrentHashMap<String, UUID> channelIdCache = new ConcurrentHashMap<>();

    @Inject
    public ConnectorQhorusMeshBridge(
            final ChannelService channelService,
            final MessageService messageService,
            final CurrentPrincipal currentPrincipal,
            final ManagedExecutor executor,
            final QhorusConfig config) {
        this.channelService      = channelService;
        this.messageService      = messageService;
        this.currentPrincipal    = currentPrincipal;
        this.executor            = executor;
        this.deliveryChannelName = config.connectorBackend().deliveryChannel().orElse("");
    }

    @Override
    public void notifyDelivered(final String connectorId, final String destination, final String content) {
        try {
            if (deliveryChannelName.isBlank()) {return;}
            if (connectorId == null) {
                LOG.warn("ConnectorMeshBridge: connectorId is null — no-op");
                return;
            }

            // Capture context synchronously on the calling (HTTP request) thread.
            // QhorusInboundCurrentPrincipal absorbs ContextNotActiveException internally —
            // no defensive catch needed here.
            final String tenancyId = currentPrincipal.tenancyId();

            final UUID channelId = channelIdCache.computeIfAbsent(tenancyId, tid ->
                                                                                     channelService.findByName(deliveryChannelName)
                                                                                                   .map(ch -> ch.id())
                                                                                                   .orElse(null));

            if (channelId == null) {
                LOG.warnf("ConnectorMeshBridge: delivery-channel '%s' not found for tenancy '%s' — no-op",
                          deliveryChannelName, tenancyId);
                return;
            }

            // Destination is intentionally excluded from content — it is either a credential
            // (Slack/Teams webhook URL) or PII (phone, email), neither of which belongs in
            // the immutable ledger.
            final String text   = "Delivered via %s: %s".formatted(connectorId, content != null ? content : "");
            final String sender = "system:connector:" + connectorId;

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
                } catch (final Exception e) {
                    LOG.warnf(e, "ConnectorMeshBridge: dispatch failed for channel '%s'", deliveryChannelName);
                }
            });
        } catch (final Exception e) {
            LOG.warnf(e, "ConnectorMeshBridge: setup failed — connector delivery still succeeded");
        }
    }

    /**
     * Package-private test helper — clears the channel ID cache between test methods.
     */
    void clearCache() {
        channelIdCache.clear();
    }
}
