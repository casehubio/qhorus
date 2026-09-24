package io.casehub.qhorus.runtime.broadcast;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.FindOrCreateResult;
import io.casehub.qhorus.api.channel.MemberRole;
import io.casehub.qhorus.api.instance.InstanceDeregisteredEvent;
import io.casehub.qhorus.api.instance.InstanceRegisteredEvent;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.ChannelMembershipService;
import io.casehub.qhorus.runtime.channel.ChannelService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

@ApplicationScoped
public class BroadcastMembershipManager {

    static final String BROADCAST_PREFIX = "broadcast/";
    private static final Logger LOG = Logger.getLogger(BroadcastMembershipManager.class);

    private final ChannelService channelService;
    private final ChannelMembershipService membershipService;

    @Inject
    public BroadcastMembershipManager(ChannelService channelService,
                                       ChannelMembershipService membershipService) {
        this.channelService = channelService;
        this.membershipService = membershipService;
    }

    void onRegistered(@ObservesAsync InstanceRegisteredEvent event) {
        Set<String> added = new HashSet<>(event.currentCapabilities());
        added.removeAll(event.previousCapabilities());

        Set<String> removed = new HashSet<>(event.previousCapabilities());
        removed.removeAll(event.currentCapabilities());

        for (String cap : added) {
            joinBroadcastChannel(event.instanceId(), cap);
        }
        for (String cap : removed) {
            leaveBroadcastChannel(event.instanceId(), cap);
        }
    }

    void onDeregistered(@ObservesAsync InstanceDeregisteredEvent event) {
        for (String cap : event.capabilities()) {
            leaveBroadcastChannel(event.instanceId(), cap);
        }
    }

    private void joinBroadcastChannel(String instanceId, String capability) {
        try {
            ChannelCreateRequest req = ChannelCreateRequest.builder(BROADCAST_PREFIX + capability)
                    .semantic(ChannelSemantic.BROADCAST)
                    .description("Broadcast channel for capability: " + capability)
                    .allowedTypes(Set.of(MessageType.STATUS, MessageType.EVENT))
                    .build();
            FindOrCreateResult result = channelService.findOrCreate(req);
            Channel channel = result.channel();
            membershipService.join(channel.id(), instanceId, MemberRole.PARTICIPANT, DEFAULT_TENANT_ID);
            LOG.debugf("Agent %s joined broadcast:%s (channel %s)", instanceId, capability, channel.id());
        } catch (Exception e) {
            LOG.warnf("Failed to join broadcast:%s for agent %s: %s", capability, instanceId, e.getMessage());
        }
    }

    private void leaveBroadcastChannel(String instanceId, String capability) {
        try {
            Optional<Channel> channel = channelService.findByName(BROADCAST_PREFIX + capability);
            channel.ifPresent(ch -> {
                membershipService.leave(ch.id(), instanceId);
                LOG.debugf("Agent %s left broadcast:%s (channel %s)", instanceId, capability, ch.id());
            });
        } catch (Exception e) {
            LOG.warnf("Failed to leave broadcast:%s for agent %s: %s", capability, instanceId, e.getMessage());
        }
    }
}
