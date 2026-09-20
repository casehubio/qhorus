package io.casehub.qhorus.api.channel;

public record MembershipSummary(
        String channelId,
        String channelName,
        String memberId,
        String role,
        String joinedAt,
        Long lastReadMessageId) {}
