package io.casehub.qhorus.api.event;

import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.message.Topic;

import java.util.UUID;

public sealed interface ChannelMutationEvent {
    record ReactionAdded(long messageId, String emoji) implements ChannelMutationEvent {}
    record ReactionRemoved(long messageId, String emoji) implements ChannelMutationEvent {}
    record MemberJoined(UUID channelId, ChannelMembership membership) implements ChannelMutationEvent {}
    record MemberLeft(UUID channelId, String memberId) implements ChannelMutationEvent {}
    record TopicCreated(UUID channelId, Topic topic) implements ChannelMutationEvent {}
    record TopicUpdated(UUID channelId, Topic topic) implements ChannelMutationEvent {}
    record TopicRemoved(UUID channelId, long topicId) implements ChannelMutationEvent {}

    record SpaceCreated(UUID spaceId, String name, String tenancyId) implements ChannelMutationEvent {}

    record SpaceRenamed(UUID spaceId, String newName) implements ChannelMutationEvent {}

    record SpaceDeleted(UUID spaceId) implements ChannelMutationEvent {}
}
