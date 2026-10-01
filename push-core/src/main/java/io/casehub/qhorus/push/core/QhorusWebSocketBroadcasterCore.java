package io.casehub.qhorus.push.core;

import io.casehub.pages.push.EventBroadcaster;
import io.casehub.pages.push.PushMessage;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.PresenceStatus;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.event.ChannelMutationEvent;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.Topic;
import io.casehub.qhorus.api.store.SpaceStore;
import io.casehub.qhorus.api.store.query.ChannelQuery;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QhorusWebSocketBroadcasterCore {

    private static final Logger LOG = Logger.getLogger(QhorusWebSocketBroadcasterCore.class.getName());

    private final QhorusDatasetBuilderCore datasetBuilder;
    private final EventBroadcaster eventBroadcaster;
    private final SpaceStore spaceStore;
    private final ChannelReader channelReader;

    public QhorusWebSocketBroadcasterCore(QhorusDatasetBuilderCore datasetBuilder,
                                           EventBroadcaster eventBroadcaster,
                                           SpaceStore spaceStore,
                                           ChannelReader channelReader) {
        this.datasetBuilder = datasetBuilder;
        this.eventBroadcaster = eventBroadcaster;
        this.spaceStore = spaceStore;
        this.channelReader = channelReader;
    }

    public void pushMessage(ChannelRef channel, OutboundMessage message) {
        var row = datasetBuilder.outboundMessageToRow(channel, message);
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_MESSAGES,
            PushMessage.append("messages", QhorusDatasetBuilderCore.MESSAGE_COLUMNS, List.of(row)));
    }

    public void broadcastChannelAppend(Channel channel) {
        Space space = channel.spaceId() != null ? spaceStore.find(channel.spaceId()).orElse(null) : null;
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_CHANNELS,
                                   PushMessage.append("channels", QhorusDatasetBuilderCore.CHANNEL_COLUMNS,
                                                      List.of(datasetBuilder.channelToRow(channel, space))));
    }

    public void broadcastChannelRemove(UUID channelId) {
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_CHANNELS,
            PushMessage.remove("channels", channelId.toString()));
    }

    public void broadcastChannelReplace(Channel channel) {
        Space space = channel.spaceId() != null ? spaceStore.find(channel.spaceId()).orElse(null) : null;
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_CHANNELS,
                                   PushMessage.replace("channels", QhorusDatasetBuilderCore.CHANNEL_COLUMNS,
                                                       channel.id().toString(), datasetBuilder.channelToRow(channel, space)));
    }

    private void broadcastChannelsInSpace(UUID spaceId) {
        var channels = spaceId != null
                       ? channelReader.scan(ChannelQuery.bySpaceId(spaceId))
                       : channelReader.scan(ChannelQuery.topLevel());
        for (Channel ch : channels) {
            broadcastChannelReplace(ch);
        }
    }

    public void broadcastPresenceReplace(String memberId, PresenceStatus status) {
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_PRESENCE,
            PushMessage.replace("presence", QhorusDatasetBuilderCore.PRESENCE_COLUMNS, memberId,
                List.of(memberId, status.name(), Instant.now().toString())));
    }

    public void broadcastMemberAppend(UUID channelId, ChannelMembership membership) {
        String membershipId = channelId.toString() + ":" + membership.memberId();
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_MEMBERS,
            PushMessage.append("members", QhorusDatasetBuilderCore.MEMBER_COLUMNS,
                List.of(List.of(membershipId, channelId.toString(),
                    membership.memberId(), membership.memberId(), membership.role().name()))));
    }

    public void broadcastMemberRemove(UUID channelId, String memberId) {
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_MEMBERS,
            PushMessage.remove("members", channelId.toString() + ":" + memberId));
    }

    public void broadcastReactionAppend(Long messageId, String emoji) {
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_REACTIONS,
            PushMessage.append("reactions", QhorusDatasetBuilderCore.REACTION_COLUMNS,
                List.of(List.of(String.valueOf(messageId), emoji))));
    }

    public void broadcastReactionRemove(Long messageId, String emoji) {
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_REACTIONS,
            PushMessage.remove("reactions", String.valueOf(messageId) + ":" + emoji));
    }

    public void broadcastCommitment(Commitment commitment) {
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_COMMITMENTS,
            PushMessage.replace("commitments", QhorusDatasetBuilderCore.COMMITMENT_COLUMNS,
                commitment.correlationId(), datasetBuilder.commitmentToRow(commitment)));
    }

    public void broadcastCommitmentAppend(Commitment commitment) {
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_COMMITMENTS,
            PushMessage.append("commitments", QhorusDatasetBuilderCore.COMMITMENT_COLUMNS,
                List.of(datasetBuilder.commitmentToRow(commitment))));
    }

    public void broadcastTopicAppend(UUID channelId, Topic topic) {
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_TOPICS,
            PushMessage.append("topics", QhorusDatasetBuilderCore.TOPIC_COLUMNS,
                List.of(datasetBuilder.topicToRow(channelId, topic))));
    }

    public void broadcastTopicReplace(UUID channelId, Topic topic) {
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_TOPICS,
            PushMessage.replace("topics", QhorusDatasetBuilderCore.TOPIC_COLUMNS,
                String.valueOf(topic.id()), datasetBuilder.topicToRow(channelId, topic)));
    }

    public void broadcastTopicRemove(UUID channelId, Long topicId) {
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_TOPICS,
            PushMessage.remove("topics", String.valueOf(topicId)));
    }

    public void broadcastSpaceAppend(Space space) {
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_SPACES,
                                   PushMessage.append("spaces", QhorusDatasetBuilderCore.SPACE_COLUMNS,
                                                      List.of(datasetBuilder.spaceToRow(space))));
    }

    public void broadcastSpaceReplace(Space space) {
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_SPACES,
                                   PushMessage.replace("spaces", QhorusDatasetBuilderCore.SPACE_COLUMNS,
                                                       space.id().toString(), datasetBuilder.spaceToRow(space)));
    }

    public void broadcastSpaceRemove(UUID spaceId) {
        eventBroadcaster.broadcast(QhorusDatasetBuilderCore.TOPIC_SPACES,
                                   PushMessage.remove("spaces", spaceId.toString()));
    }

    public void onMutation(ChannelMutationEvent event) {
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
            case ChannelMutationEvent.ChannelMoved e -> {
                broadcastChannelsInSpace(e.targetSpaceId());
                if (!Objects.equals(e.sourceSpaceId(), e.targetSpaceId())) {
                    broadcastChannelsInSpace(e.sourceSpaceId());
                }
            }
            default -> LOG.log(Level.WARNING, "Unhandled ChannelMutationEvent variant: {0}",
                    event.getClass().getSimpleName());
        }
    }
}
