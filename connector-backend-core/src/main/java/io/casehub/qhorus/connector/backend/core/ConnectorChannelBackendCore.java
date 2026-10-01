package io.casehub.qhorus.connector.backend.core;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.ConnectorService;
import io.casehub.connectors.InboundMessage;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.FindOrCreateResult;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.DeliveryGuarantee;
import io.casehub.qhorus.api.gateway.HumanParticipatingChannelBackend;
import io.casehub.qhorus.api.gateway.InboundHumanMessage;
import io.casehub.qhorus.api.gateway.InboundNormaliser;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.store.ChannelBindingStore;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.PersistenceException;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectorChannelBackendCore implements HumanParticipatingChannelBackend {

    private static final Logger LOG = Logger.getLogger(ConnectorChannelBackendCore.class.getName());
    private static final String BACKEND_ID = "connector-human";

    private final ChannelGateway gateway;
    private final ChannelService channelService;
    private final ChannelBindingStore bindingStore;
    private final ConnectorService connectorService;
    private final MeterRegistry meterRegistry;
    private final AutoChannelPolicy autoChannelPolicy;
    private final Map<String, ConnectorNormaliser> normalisersByConnectorId;

    private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

    public ConnectorChannelBackendCore(ChannelGateway gateway, ChannelService channelService,
                                        ChannelBindingStore bindingStore, ConnectorService connectorService,
                                        MeterRegistry meterRegistry, AutoChannelPolicy autoChannelPolicy,
                                        Map<String, ConnectorNormaliser> normalisersByConnectorId) {
        this.gateway = gateway;
        this.channelService = channelService;
        this.bindingStore = bindingStore;
        this.connectorService = connectorService;
        this.meterRegistry = meterRegistry;
        this.autoChannelPolicy = autoChannelPolicy;
        this.normalisersByConnectorId = normalisersByConnectorId;
    }

    @Override
    public String backendId() {
        return BACKEND_ID;
    }

    @Override
    public ActorType actorType() {
        return ActorType.HUMAN;
    }

    @Override
    public DeliveryGuarantee deliveryGuarantee() {
        return DeliveryGuarantee.AT_LEAST_ONCE;
    }

    @Override
    public InboundNormaliser normaliserFor(UUID channelId) {
        CacheEntry entry = cache.get(channelId);
        if (entry == null) {
            return null;
        }
        return normalisersByConnectorId.get(entry.inboundConnectorId());
    }

    @Override
    public void open(ChannelRef channel, Map<String, String> metadata) {
    }

    @Override
    public void close(ChannelRef channel) {
        cache.remove(channel.id());
    }

    public void onChannelInitialised(UUID channelId) {
        bindingStore.findByChannelId(channelId).ifPresentOrElse(binding -> {
            cache.put(channelId, new CacheEntry(
                    binding.inboundConnectorId(),
                    binding.externalKey(),
                    binding.outboundConnectorId(),
                    binding.outboundDestination()));
            gateway.deregisterBackend(channelId, BACKEND_ID);
            gateway.registerBackend(channelId, this, "human_participating");
        }, () -> {});
    }

    public void onInboundMessage(InboundMessage msg) {
        String lookupKey = ConnectorKeyStrategy.deriveKey(msg);

        channelService.findByConnectorKey(msg.connectorId(), lookupKey)
                .or(() -> tryAutoCreate(msg, lookupKey))
                .ifPresentOrElse(
                        channel -> route(channel, msg),
                        () -> {
                            LOG.log(Level.WARNING, "No channel for connector={0} key={1} — discarding",
                                    new Object[]{msg.connectorId(), lookupKey});
                            meterRegistry.counter("inbound_messages_discarded_total",
                                    "connector_id", msg.connectorId()).increment();
                        });
    }

    private void route(Channel channel, InboundMessage msg) {
        String correlationId = msg.metadata() != null
                ? msg.metadata().get("correlation-id") : null;
        gateway.receiveHumanMessage(
                new ChannelRef(channel.id(), channel.name()),
                new InboundHumanMessage(
                        msg.externalSenderId(),
                        msg.content(),
                        msg.receivedAt(),
                        msg.metadata(),
                        correlationId,
                        null));
    }

    private Optional<Channel> tryAutoCreate(InboundMessage msg, String lookupKey) {
        Optional<AutoChannelSpec> specOpt = autoChannelPolicy.onFirstContact(msg, lookupKey);
        if (specOpt.isEmpty()) {
            return Optional.empty();
        }
        AutoChannelSpec spec = specOpt.get();
        ChannelCreateRequest req = ChannelCreateRequest.builder(spec.channelName())
                .description(spec.description())
                .semantic(spec.semantic())
                .allowedTypes(spec.allowedTypes())
                .deniedTypes(spec.deniedTypes())
                .inboundConnectorId(msg.connectorId())
                .externalKey(lookupKey)
                .outboundConnectorId(spec.outboundConnectorId())
                .outboundDestination(spec.outboundDestination())
                .build();
        try {
            FindOrCreateResult result = channelService.findOrCreate(req);
            if (result.wasCreated()) {
                meterRegistry.counter("inbound_channels_auto_created_total",
                        "connector_id", msg.connectorId()).increment();
            }
            Channel channel = result.channel();
            gateway.initChannel(channel.id(), new ChannelRef(channel.id(), channel.name()));
            return Optional.of(channel);
        } catch (PersistenceException ex) {
            if (isConcurrentInsert(ex)) {
                Optional<Channel> recovered = channelService.findByConnectorKey(msg.connectorId(), lookupKey);
                if (recovered.isEmpty()) {
                    LOG.log(Level.SEVERE, "Race recovery failed: binding exists but channel not found for connector={0} key={1} — discarding",
                            new Object[]{msg.connectorId(), lookupKey});
                    return Optional.empty();
                }
                return recovered;
            }
            LOG.log(Level.SEVERE, "DB error auto-creating channel for connector={0} key={1} — discarding",
                    new Object[]{msg.connectorId(), lookupKey});
            return Optional.empty();
        }
    }

    public static boolean isConcurrentInsert(PersistenceException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SQLIntegrityConstraintViolationException c) {
                String msg = c.getMessage() != null ? c.getMessage().toLowerCase() : "";
                return msg.contains("uq_binding_key") || msg.contains("unique");
            }
            if (cause instanceof java.sql.SQLException s
                    && !(cause instanceof SQLIntegrityConstraintViolationException)) {
                String msg = s.getMessage() != null ? s.getMessage() : "";
                if (msg.contains("uq_binding_key")) return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Override
    public void post(ChannelRef channel, OutboundMessage message) {
        CacheEntry entry = cache.get(channel.id());
        if (entry == null) {
            LOG.log(Level.FINE, "No cache entry for channel {0} ({1}) — not a connector-backed channel, skipping",
                    new Object[]{channel.id(), channel.name()});
            return;
        }
        String title = OutboundTitle.forConnector(entry.outboundConnectorId(), channel);
        try {
            connectorService.send(entry.outboundConnectorId(),
                    new ConnectorMessage(entry.outboundDestination(), title, message.content()));
        } catch (IllegalArgumentException ex) {
            LOG.log(Level.SEVERE, "Failed to send via connector {0} to channel {1} ({2})",
                    new Object[]{entry.outboundConnectorId(), channel.id(), channel.name()});
        }
    }

    public double discardedCount(String connectorId) {
        return meterRegistry.counter("inbound_messages_discarded_total",
                "connector_id", connectorId).count();
    }

    public double autoCreatedCount(String connectorId) {
        return meterRegistry.counter("inbound_channels_auto_created_total",
                "connector_id", connectorId).count();
    }

    private record CacheEntry(String inboundConnectorId, String externalKey,
                               String outboundConnectorId, String outboundDestination) {}
}
