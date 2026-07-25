package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.channel.ChannelMembership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipReader {

    Optional<ChannelMembership> find(UUID channelId, String memberId);

    List<ChannelMembership> findByChannel(UUID channelId);

    List<ChannelMembership> findByMember(String memberId, String tenancyId);
}
