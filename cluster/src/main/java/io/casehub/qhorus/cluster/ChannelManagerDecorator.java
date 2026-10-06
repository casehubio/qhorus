package io.casehub.qhorus.cluster;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.EnforcementMode;
import io.casehub.qhorus.api.channel.FindOrCreateResult;
import io.casehub.qhorus.api.message.MessageType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public class ChannelManagerDecorator implements ChannelManager {

    private final ChannelManager delegate;
    private final ClusterManager clusterManager;
    private final WriteProxyClient proxyClient;
    private final boolean routingEnabled;

    public ChannelManagerDecorator(ChannelManager delegate,
                                   ClusterManager clusterManager,
                                   WriteProxyClient proxyClient,
                                   boolean routingEnabled) {
        this.delegate = delegate;
        this.clusterManager = clusterManager;
        this.proxyClient = proxyClient;
        this.routingEnabled = routingEnabled;
    }

    @Override
    public Channel create(ChannelCreateRequest request) {
        if (!routingEnabled) {
            return delegate.create(request);
        }
        if (!clusterManager.canServeWrites()) {
            throw new QuorumViolationException("minority partition");
        }
        UUID channelId = request.preAssignedId() != null
                ? request.preAssignedId() : UUID.randomUUID();
        var withId = ChannelCreateRequest.builder(request.name())
                .description(request.description())
                .semantic(request.semantic())
                .barrierContributors(request.barrierContributors())
                .allowedWriters(request.allowedWriters())
                .adminInstances(request.adminInstances())
                .rateLimitPerChannel(request.rateLimitPerChannel())
                .rateLimitPerInstance(request.rateLimitPerInstance())
                .allowedTypes(request.allowedTypes())
                .deniedTypes(request.deniedTypes())
                .spaceId(request.spaceId())
                .reviewerInstances(request.reviewerInstances())
                .protocols(request.protocols())
                .protocolParticipants(request.protocolParticipants())
                .trackDelivery(request.trackDelivery())
                .enforcementMode(request.enforcementMode())
                .enforcementExclusions(request.enforcementExclusions())
                .routingTrustThreshold(request.routingTrustThreshold())
                .metadata(request.metadata())
                .preAssignedId(channelId)
                .build();
        NodeInfo owner = clusterManager.owner(channelId);
        if (clusterManager.isLocal(owner)) {
            return delegate.create(withId);
        }
        return proxyClient.createChannel(owner, withId);
    }

    @Override
    public FindOrCreateResult findOrCreate(ChannelCreateRequest request) {
        return delegate.findOrCreate(request);
    }

    @Override
    public long delete(UUID channelId, boolean force) {
        if (!routingEnabled) {
            return delegate.delete(channelId, force);
        }
        if (!clusterManager.canServeWrites()) {
            throw new QuorumViolationException("minority partition");
        }
        NodeInfo owner = clusterManager.owner(channelId);
        if (clusterManager.isLocal(owner)) {
            return delegate.delete(channelId, force);
        }
        return proxyClient.deleteChannel(owner, channelId, force);
    }

    @Override
    public Channel pause(UUID channelId) {
        return routeChannelMutation(channelId, id -> delegate.pause(id));
    }

    @Override
    public Channel resume(UUID channelId) {
        return routeChannelMutation(channelId, id -> delegate.resume(id));
    }

    @Override
    public Channel setTypeConstraints(UUID id, Set<MessageType> a, Set<MessageType> d) {
        return routeChannelMutation(id, i -> delegate.setTypeConstraints(i, a, d));
    }

    @Override
    public Channel setRateLimits(UUID id, Integer pc, Integer pi) {
        return routeChannelMutation(id, i -> delegate.setRateLimits(i, pc, pi));
    }

    @Override
    public Channel setAllowedWriters(UUID id, List<String> w) {
        return routeChannelMutation(id, i -> delegate.setAllowedWriters(i, w));
    }

    @Override
    public Channel setAdminInstances(UUID id, List<String> a) {
        return routeChannelMutation(id, i -> delegate.setAdminInstances(i, a));
    }

    @Override
    public Channel setReviewerInstances(UUID id, List<String> r) {
        return routeChannelMutation(id, i -> delegate.setReviewerInstances(i, r));
    }

    @Override
    public Channel setProtocols(UUID id, List<String> p) {
        return routeChannelMutation(id, i -> delegate.setProtocols(i, p));
    }

    @Override
    public Channel setProtocolParticipants(UUID id, List<String> p) {
        return routeChannelMutation(id, i -> delegate.setProtocolParticipants(i, p));
    }

    @Override
    public Channel setEnforcementMode(UUID id, EnforcementMode m) {
        return routeChannelMutation(id, i -> delegate.setEnforcementMode(i, m));
    }

    @Override
    public Channel setEnforcementExclusions(UUID id, List<String> e) {
        return routeChannelMutation(id, i -> delegate.setEnforcementExclusions(i, e));
    }

    @Override
    public Channel setRoutingTrustThreshold(UUID id, Double t) {
        return routeChannelMutation(id, i -> delegate.setRoutingTrustThreshold(i, t));
    }

    @Override
    public Channel setRedistributionCapacityThreshold(UUID id, Double t) {
        return routeChannelMutation(id, i -> delegate.setRedistributionCapacityThreshold(i, t));
    }

    @Override
    public Channel setRoutingCapacityThreshold(UUID id, Double t) {
        return routeChannelMutation(id, i -> delegate.setRoutingCapacityThreshold(i, t));
    }

    @Override
    public Channel setPolicyOverrides(UUID id, Map<String, String> o) {
        return routeChannelMutation(id, i -> delegate.setPolicyOverrides(i, o));
    }

    @Override
    public void setTrackDelivery(UUID id, Boolean t) {
        routeChannelMutation(id, i -> { delegate.setTrackDelivery(i, t); return null; });
    }

    @Override
    public void updateLastActivity(UUID id, String t) {
        delegate.updateLastActivity(id, t);
    }

    private Channel routeChannelMutation(UUID channelId, Function<UUID, Channel> localAction) {
        if (!routingEnabled) {
            return localAction.apply(channelId);
        }
        NodeInfo owner = clusterManager.owner(channelId);
        if (clusterManager.isLocal(owner)) {
            return localAction.apply(channelId);
        }
        throw new UnsupportedOperationException("Remote config mutation proxy not yet wired");
    }
}
