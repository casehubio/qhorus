package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.channel.MemberRole;
import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReactiveChannelMembershipStore {
    Uni<ChannelMembership> put(ChannelMembership membership);
    Uni<Optional<ChannelMembership>> find(UUID channelId, String memberId);
    Uni<List<ChannelMembership>> findByChannel(UUID channelId);
    Uni<List<ChannelMembership>> findByMember(String memberId, String tenancyId);
    Uni<Void> updateRole(UUID channelId, String memberId, MemberRole role);
    Uni<Void> updateLastReadMessageId(UUID channelId, String memberId, Long messageId);
    Uni<Boolean> delete(UUID channelId, String memberId);
    Uni<Void> deleteAll(UUID channelId);
}
