package io.casehub.qhorus.api.channel;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.smallrye.mutiny.Uni;
import io.casehub.qhorus.api.message.MessageType;

public interface ReactiveChannelManager {
    Uni<Channel> create(ChannelCreateRequest request);
    Uni<FindOrCreateResult> findOrCreate(ChannelCreateRequest request);
    Uni<Long> delete(UUID channelId, boolean force);
    Uni<Channel> pause(UUID channelId);
    Uni<Channel> resume(UUID channelId);

    Uni<Channel> setTypeConstraints(UUID channelId, Set<MessageType> allowedTypes, Set<MessageType> deniedTypes);
    Uni<Channel> setRateLimits(UUID channelId, Integer perChannel, Integer perInstance);
    Uni<Channel> setAllowedWriters(UUID channelId, List<String> allowedWriters);
    Uni<Channel> setAdminInstances(UUID channelId, List<String> adminInstances);
}
