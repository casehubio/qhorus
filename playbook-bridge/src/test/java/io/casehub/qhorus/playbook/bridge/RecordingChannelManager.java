package io.casehub.qhorus.playbook.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelVisibility;
import io.casehub.qhorus.api.channel.EnforcementMode;
import io.casehub.qhorus.api.channel.FindOrCreateResult;
import io.casehub.qhorus.api.channel.ParticipationPolicyMode;
import io.casehub.qhorus.api.message.MessageType;

class RecordingChannelManager implements ChannelManager {

    final ConcurrentHashMap<UUID, List<String>> protocolsByChannel = new ConcurrentHashMap<>();

    List<String> lastSetProtocols(UUID channelId) {
        return protocolsByChannel.get(channelId);
    }

    @Override
    public Channel setProtocols(UUID channelId, List<String> protocols) {
        protocolsByChannel.put(channelId, new ArrayList<>(protocols));
        return Channel.builder("test").id(channelId).protocols(protocols).build();
    }

    @Override public Channel create(ChannelCreateRequest r) { throw new UnsupportedOperationException(); }
    @Override public FindOrCreateResult findOrCreate(ChannelCreateRequest r) { throw new UnsupportedOperationException(); }
    @Override public long delete(UUID id, boolean f) { throw new UnsupportedOperationException(); }
    @Override public Channel pause(UUID id) { throw new UnsupportedOperationException(); }
    @Override public Channel resume(UUID id) { throw new UnsupportedOperationException(); }
    @Override public Channel setTypeConstraints(UUID id, Set<MessageType> a, Set<MessageType> d) { throw new UnsupportedOperationException(); }
    @Override public Channel setRateLimits(UUID id, Integer pc, Integer pi) { throw new UnsupportedOperationException(); }
    @Override public Channel setAllowedWriters(UUID id, List<String> w) { throw new UnsupportedOperationException(); }
    @Override public Channel setAdminInstances(UUID id, List<String> a) { throw new UnsupportedOperationException(); }
    @Override public Channel setReviewerInstances(UUID id, List<String> r) { throw new UnsupportedOperationException(); }
    @Override public Channel setProtocolParticipants(UUID id, List<String> p) { throw new UnsupportedOperationException(); }
    @Override public Channel setEnforcementMode(UUID id, EnforcementMode m) { throw new UnsupportedOperationException(); }
    @Override public Channel setParticipationPolicy(UUID id, ParticipationPolicyMode p) { throw new UnsupportedOperationException(); }
    @Override public Channel setEnforcementExclusions(UUID id, List<String> e) { throw new UnsupportedOperationException(); }
    @Override public Channel setRoutingTrustThreshold(UUID id, Double t) { throw new UnsupportedOperationException(); }
    @Override public Channel setRedistributionCapacityThreshold(UUID id, Double t) { throw new UnsupportedOperationException(); }
    @Override public Channel setRoutingCapacityThreshold(UUID id, Double t) { throw new UnsupportedOperationException(); }
    @Override public Channel setPolicyOverrides(UUID id, Map<String, String> o) { throw new UnsupportedOperationException(); }
    @Override public void setTrackDelivery(UUID id, Boolean t) { throw new UnsupportedOperationException(); }
    @Override public void updateLastActivity(UUID id, String t) { throw new UnsupportedOperationException(); }
    @Override public Channel setVisibility(UUID id, ChannelVisibility v) { throw new UnsupportedOperationException(); }
}
