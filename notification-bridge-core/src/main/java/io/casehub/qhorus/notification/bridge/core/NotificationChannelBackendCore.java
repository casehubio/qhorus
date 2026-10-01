package io.casehub.qhorus.notification.bridge.core;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.subscription.SubscribableEvent;
import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.gateway.AgentChannelBackend;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.DeliveryGuarantee;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.store.ChannelMembershipStore;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static io.casehub.platform.api.identity.TenancyConstants.PLATFORM_TENANT_ID;

public class NotificationChannelBackendCore implements AgentChannelBackend {

    private static final String BROADCAST_PREFIX = "broadcast/";

    private final ChannelMembershipStore membershipStore;
    private final Consumer<SubscribableEvent> eventSink;

    public NotificationChannelBackendCore(ChannelMembershipStore membershipStore,
                                           Consumer<SubscribableEvent> eventSink) {
        this.membershipStore = membershipStore;
        this.eventSink = eventSink;
    }

    @Override
    public String backendId() { return "platform-notifications"; }

    @Override
    public ActorType actorType() { return ActorType.SYSTEM; }

    @Override
    public DeliveryGuarantee deliveryGuarantee() { return DeliveryGuarantee.BEST_EFFORT; }

    @Override
    public void open(ChannelRef channel, Map<String, String> metadata) {}

    @Override
    public void close(ChannelRef channel) {}

    @Override
    public void post(ChannelRef channel, OutboundMessage message) {
        String channelName = channel.name();
        if (!channelName.startsWith(BROADCAST_PREFIX)) {
            return;
        }
        String capabilityTag = channelName.substring(BROADCAST_PREFIX.length());

        List<ChannelMembership> members = membershipStore.findByChannel(channel.id());

        for (ChannelMembership member : members) {
            if (member.memberId().equals(message.sender())) {
                continue;
            }
            eventSink.accept(new QhorusBroadcastEvent(
                    PLATFORM_TENANT_ID,
                    member.memberId(),
                    capabilityTag,
                    channel.id(),
                    channelName,
                    message.sender(),
                    message.type().name(),
                    message.content()));
        }
    }
}
