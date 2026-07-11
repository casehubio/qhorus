package io.casehub.qhorus.runtime.channel;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.channel.MemberRole;
import io.casehub.qhorus.api.channel.UnreadCount;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.ChannelMembershipStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;

@ApplicationScoped
public class ChannelMembershipService {

    @Inject
    ChannelMembershipStore membershipStore;

    @Inject
    MessageStore messageStore;

    public ChannelMembership join(UUID channelId, String memberId, MemberRole role, String tenancyId) {
        var existing = membershipStore.find(channelId, memberId);
        if (existing.isPresent()) {
            if (existing.get().role() != role) {
                membershipStore.updateRole(channelId, memberId, role);
            }
            return membershipStore.find(channelId, memberId).orElseThrow();
        }
        Long maxId = messageStore.findLastMessage(channelId).map(Message::id).orElse(0L);
        return membershipStore.put(new ChannelMembership(
                null, channelId, memberId, role, tenancyId, Instant.now(), maxId));
    }

    public void leave(UUID channelId, String memberId) {
        membershipStore.delete(channelId, memberId);
    }

    public List<ChannelMembership> listMembers(UUID channelId) {
        return membershipStore.findByChannel(channelId);
    }

    public void markRead(UUID channelId, String memberId, Long messageId) {
        var m = membershipStore.find(channelId, memberId);
        if (m.isPresent() && messageId != null) {
            Long current = m.get().lastReadMessageId();
            if (current == null || messageId > current) {
                membershipStore.updateLastReadMessageId(channelId, memberId, messageId);
            }
        }
    }

    public Map<UUID, UnreadCount> getUnreadCounts(String memberId, String tenancyId) {
        List<ChannelMembership> memberships = membershipStore.findByMember(memberId, tenancyId);
        Map<UUID, UnreadCount> result = new LinkedHashMap<>();
        for (ChannelMembership m : memberships) {
            long afterId = m.lastReadMessageId() != null ? m.lastReadMessageId() : 0L;
            long total = messageStore.count(MessageQuery.builder()
                    .channelId(m.channelId())
                    .afterId(afterId)
                    .excludeTypes(List.of(MessageType.EVENT))
                    .build());
            long own = messageStore.count(MessageQuery.builder()
                    .channelId(m.channelId())
                    .afterId(afterId)
                    .excludeTypes(List.of(MessageType.EVENT))
                    .sender(memberId)
                    .build());
            long unread = total - own;
            if (unread > 0) {
                Long latest = messageStore.findLastMessage(m.channelId()).map(Message::id).orElse(null);
                result.put(m.channelId(), new UnreadCount(m.channelId(), null, unread, latest));
            }
        }
        return result;
    }
}
