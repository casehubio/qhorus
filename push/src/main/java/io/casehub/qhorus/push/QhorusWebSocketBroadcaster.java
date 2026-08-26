package io.casehub.qhorus.push;

import io.casehub.pages.push.EventBroadcaster;
import io.casehub.pages.push.PushMessage;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.channel.PresenceStatus;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.event.ChannelMutationEvent;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.Topic;
import io.casehub.qhorus.api.store.SpaceStore;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class QhorusWebSocketBroadcaster {

    @Inject QhorusDatasetBuilder datasetBuilder;
    @Inject EventBroadcaster eventBroadcaster;
    @Inject SpaceStore spaceStore;

    public void pushMessage(ChannelRef channel, OutboundMessage message) {
        var row = datasetBuilder.outboundMessageToRow(channel, message);
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_MESSAGES,
            PushMessage.append("messages", QhorusDatasetBuilder.MESSAGE_COLUMNS, List.of(row)));
    }

    public void broadcastChannelAppend(Channel channel) {
        Space space = channel.spaceId() != null ? spaceStore.find(channel.spaceId()).orElse(null) : null;
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_CHANNELS,
            PushMessage.append("channels", QhorusDatasetBuilder.CHANNEL_COLUMNS,
                List.of(List.of(channel.id().toString(), channel.name(), "",
                    channel.description() != null ? channel.description() : "", "false",
                    channel.spaceId() != null ? channel.spaceId().toString() : "",
                    space != null ? space.name() : "",
                    space != null && space.parentSpaceId() != null ? space.parentSpaceId().toString() : ""))));
    }

    public void broadcastChannelRemove(UUID channelId) {
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_CHANNELS,
            PushMessage.remove("channels", channelId.toString()));
    }

    public void broadcastPresenceReplace(String memberId, PresenceStatus status) {
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_PRESENCE,
            PushMessage.replace("presence", QhorusDatasetBuilder.PRESENCE_COLUMNS, memberId,
                List.of(memberId, status.name(), Instant.now().toString())));
    }

    public void broadcastMemberAppend(UUID channelId, ChannelMembership membership) {
        String membershipId = channelId.toString() + ":" + membership.memberId();
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_MEMBERS,
            PushMessage.append("members", QhorusDatasetBuilder.MEMBER_COLUMNS,
                List.of(List.of(membershipId, channelId.toString(),
                    membership.memberId(), membership.memberId(), membership.role().name()))));
    }

    public void broadcastMemberRemove(UUID channelId, String memberId) {
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_MEMBERS,
            PushMessage.remove("members", channelId.toString() + ":" + memberId));
    }

    public void broadcastReactionAppend(Long messageId, String emoji) {
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_REACTIONS,
            PushMessage.append("reactions", QhorusDatasetBuilder.REACTION_COLUMNS,
                List.of(List.of(String.valueOf(messageId), emoji))));
    }

    public void broadcastReactionRemove(Long messageId, String emoji) {
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_REACTIONS,
            PushMessage.remove("reactions", String.valueOf(messageId) + ":" + emoji));
    }

    public void broadcastCommitment(Commitment commitment) {
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_COMMITMENTS,
            PushMessage.replace("commitments", QhorusDatasetBuilder.COMMITMENT_COLUMNS,
                commitment.correlationId(), datasetBuilder.commitmentToRow(commitment)));
    }

    public void broadcastCommitmentAppend(Commitment commitment) {
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_COMMITMENTS,
            PushMessage.append("commitments", QhorusDatasetBuilder.COMMITMENT_COLUMNS,
                List.of(datasetBuilder.commitmentToRow(commitment))));
    }

    public void broadcastTopicAppend(UUID channelId, Topic topic) {
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_TOPICS,
            PushMessage.append("topics", QhorusDatasetBuilder.TOPIC_COLUMNS,
                List.of(datasetBuilder.topicToRow(channelId, topic))));
    }

    public void broadcastTopicReplace(UUID channelId, Topic topic) {
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_TOPICS,
            PushMessage.replace("topics", QhorusDatasetBuilder.TOPIC_COLUMNS,
                String.valueOf(topic.id()), datasetBuilder.topicToRow(channelId, topic)));
    }

    public void broadcastTopicRemove(UUID channelId, Long topicId) {
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_TOPICS,
            PushMessage.remove("topics", String.valueOf(topicId)));
    }

    public void broadcastSpaceAppend(Space space) {
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_SPACES,
                                   PushMessage.append("spaces", QhorusDatasetBuilder.SPACE_COLUMNS,
                                                      List.of(datasetBuilder.spaceToRow(space))));
    }

    public void broadcastSpaceReplace(Space space) {
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_SPACES,
                                   PushMessage.replace("spaces", QhorusDatasetBuilder.SPACE_COLUMNS,
                                                       space.id().toString(), datasetBuilder.spaceToRow(space)));
    }

    public void broadcastSpaceRemove(UUID spaceId) {
        eventBroadcaster.broadcast(QhorusDatasetBuilder.TOPIC_SPACES,
                                   PushMessage.remove("spaces", spaceId.toString()));
    }


    void onMutation(@Observes ChannelMutationEvent event) {
        switch (event) {
            case ChannelMutationEvent.ReactionAdded e -> broadcastReactionAppend(e.messageId(), e.emoji());
            case ChannelMutationEvent.ReactionRemoved e -> broadcastReactionRemove(e.messageId(), e.emoji());
            case ChannelMutationEvent.MemberJoined e -> broadcastMemberAppend(e.channelId(), e.membership());
            case ChannelMutationEvent.MemberLeft e -> broadcastMemberRemove(e.channelId(), e.memberId());
            case ChannelMutationEvent.TopicCreated e -> broadcastTopicAppend(e.channelId(), e.topic());
            case ChannelMutationEvent.TopicUpdated e -> broadcastTopicReplace(e.channelId(), e.topic());
            case ChannelMutationEvent.TopicRemoved e -> broadcastTopicRemove(e.channelId(), e.topicId());
            case ChannelMutationEvent.SpaceCreated e -> spaceStore.find(e.spaceId()).ifPresent(this::broadcastSpaceAppend);
            case ChannelMutationEvent.SpaceRenamed e -> spaceStore.find(e.spaceId()).ifPresent(this::broadcastSpaceReplace);
            case ChannelMutationEvent.SpaceDeleted e -> broadcastSpaceRemove(e.spaceId());
            default -> Log.warnf("Unhandled ChannelMutationEvent variant: %s", event.getClass().getSimpleName());
        }
    }
}
