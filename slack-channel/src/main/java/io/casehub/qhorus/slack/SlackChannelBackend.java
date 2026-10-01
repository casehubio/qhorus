package io.casehub.qhorus.slack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.transaction.Transactional;

import io.casehub.connectors.InboundMessage;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.gateway.ChannelInitialisedEvent;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.DeliveryGuarantee;
import io.casehub.qhorus.api.gateway.HumanParticipatingChannelBackend;
import io.casehub.qhorus.api.gateway.InboundNormaliser;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.slack.core.SlackChannelBackendCore;

@ApplicationScoped
public class SlackChannelBackend implements HumanParticipatingChannelBackend {

    private final SlackChannelBackendCore core;

    public SlackChannelBackend(SlackChannelBackendCore core) {
        this.core = core;
    }

    public SlackChannelBackendCore core() {
        return core;
    }

    @Override
    public String backendId() {
        return core.backendId();
    }

    @Override
    public InboundNormaliser normaliserFor(UUID channelId) {
        return core.normaliserFor(channelId);
    }

    @Override
    public ActorType actorType() {
        return core.actorType();
    }

    @Override
    public DeliveryGuarantee deliveryGuarantee() {
        return core.deliveryGuarantee();
    }

    @Override
    public void open(ChannelRef channel, Map<String, String> metadata) {
        core.open(channel, metadata);
    }

    @Transactional
    public void onChannelInitialised(@Observes ChannelInitialisedEvent event) {
        core.onChannelInitialised(event);
    }

    @Override
    public void post(ChannelRef channel, OutboundMessage message) {
        core.post(channel, message);
    }

    public CompletionStage<Void> onInboundMessage(@ObservesAsync InboundMessage msg) {
        return core.onInboundMessage(msg);
    }

    public void evict(UUID channelId) {
        core.evict(channelId);
    }

    @Override
    public void close(ChannelRef channel) {
        core.close(channel);
    }
}
