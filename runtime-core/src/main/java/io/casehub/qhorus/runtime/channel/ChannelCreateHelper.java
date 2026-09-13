package io.casehub.qhorus.runtime.channel;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelConnectorBinding;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.FindOrCreateResult;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.store.ChannelBindingStore;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

public class ChannelCreateHelper {

    private final ChannelStore channelStore;
    private final ChannelBindingStore channelBindingStore;
    private final ChannelGateway channelGateway;
    private final CurrentPrincipal currentPrincipal;

    public ChannelCreateHelper(ChannelStore channelStore,
                               ChannelBindingStore channelBindingStore,
                               ChannelGateway channelGateway,
                               CurrentPrincipal currentPrincipal) {
        this.channelStore = channelStore;
        this.channelBindingStore = channelBindingStore;
        this.channelGateway = channelGateway;
        this.currentPrincipal = currentPrincipal;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public FindOrCreateResult createInNewTransaction(ChannelCreateRequest req, boolean autoCreated) {
        if (req.hasConnectorBinding()) {
            return createWithBinding(req, autoCreated);
        }
        return new FindOrCreateResult(createChannelOnly(req, autoCreated), true);
    }

    private FindOrCreateResult createWithBinding(ChannelCreateRequest req, boolean autoCreated) {
        UUID channelId = UUID.randomUUID();

        Channel channel = Channel.fromRequest(req, currentPrincipal.tenancyId())
                                 .toBuilder().id(channelId).build();
        if (autoCreated) {
            channel = channel.toBuilder().autoCreated(true).build();
        }
        channel = channelStore.put(channel);

        ChannelConnectorBinding newBinding = new ChannelConnectorBinding(
                channelId, req.inboundConnectorId(), req.externalKey(),
                req.outboundConnectorId(), req.outboundDestination());
        Optional<ChannelConnectorBinding> existing = channelBindingStore.putIfAbsent(newBinding);

        if (existing.isPresent()) {
            channelStore.delete(channelId);
            Channel winner = channelStore.find(existing.get().channelId())
                                         .orElseThrow(() -> new IllegalStateException(
                                                 "Stale binding: binding exists for key '" + req.externalKey()
                                                 + "' (connector=" + req.inboundConnectorId()
                                                 + ") but referenced channel was deleted"));
            return new FindOrCreateResult(winner, false);
        }

        channelGateway.initChannel(channel.id(), new ChannelRef(channel.id(), channel.name()));
        return new FindOrCreateResult(channel, true);
    }

    private Channel createChannelOnly(ChannelCreateRequest req, boolean autoCreated) {
        Channel channel = Channel.fromRequest(req, currentPrincipal.tenancyId());
        if (autoCreated) {
            channel = channel.toBuilder().autoCreated(true).build();
        }
        channel = channelStore.put(channel);
        channelGateway.initChannel(channel.id(), new ChannelRef(channel.id(), channel.name()));
        return channel;
    }
}
