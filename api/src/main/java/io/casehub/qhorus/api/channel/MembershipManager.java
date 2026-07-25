package io.casehub.qhorus.api.channel;

import java.util.UUID;

public interface MembershipManager {

    ChannelMembership join(UUID channelId, String memberId);

    void leave(UUID channelId, String memberId);

    void setRole(UUID channelId, String memberId, MemberRole role);

    void updateLastReadMessageId(UUID channelId, String memberId, Long messageId);
}
