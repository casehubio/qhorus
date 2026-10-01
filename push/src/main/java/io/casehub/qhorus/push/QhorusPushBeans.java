package io.casehub.qhorus.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.pages.push.EventBroadcaster;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.PresenceTracker;
import io.casehub.qhorus.api.channel.TopicManager;
import io.casehub.qhorus.api.channel.UnreadCountProvider;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.store.CommitmentReader;
import io.casehub.qhorus.api.store.MembershipReader;
import io.casehub.qhorus.api.store.ReactionReader;
import io.casehub.qhorus.api.store.SpaceStore;
import io.casehub.qhorus.api.store.TopicReader;
import io.casehub.qhorus.push.core.QhorusDatasetBuilderCore;
import io.casehub.qhorus.push.core.QhorusWebSocketBroadcasterCore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class QhorusPushBeans {

    @Produces
    @ApplicationScoped
    public QhorusDatasetBuilderCore datasetBuilder(ChannelReader channelReader,
                                                    ConsumerMessaging messaging,
                                                    MembershipReader memberReader,
                                                    ReactionReader reactionReader,
                                                    CommitmentReader commitmentReader,
                                                    TopicReader topicReader,
                                                    TopicManager topicManager,
                                                    PresenceTracker presenceTracker,
                                                    SpaceStore spaceStore,
                                                    UnreadCountProvider unreadCountProvider,
                                                    ObjectMapper objectMapper) {
        return new QhorusDatasetBuilderCore(channelReader, messaging, memberReader,
                reactionReader, commitmentReader, topicReader, topicManager,
                presenceTracker, spaceStore, unreadCountProvider, objectMapper);
    }

    @Produces
    @ApplicationScoped
    public QhorusWebSocketBroadcasterCore webSocketBroadcaster(QhorusDatasetBuilderCore datasetBuilder,
                                                                EventBroadcaster eventBroadcaster,
                                                                SpaceStore spaceStore,
                                                                ChannelReader channelReader) {
        return new QhorusWebSocketBroadcasterCore(datasetBuilder, eventBroadcaster, spaceStore, channelReader);
    }
}
