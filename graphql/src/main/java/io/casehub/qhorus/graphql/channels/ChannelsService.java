package io.casehub.qhorus.graphql.channels;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelPage;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.spi.channels.ChannelsApi;
import io.casehub.qhorus.api.store.query.ChannelQuery;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ChannelsService implements ChannelsApi {

    private final ChannelReader channelReader;
    private final ConsumerMessaging consumerMessaging;
    private final ChannelManager channelManager;

    public ChannelsService(ChannelReader channelReader,
                           ConsumerMessaging consumerMessaging,
                           ChannelManager channelManager) {
        this.channelReader = channelReader;
        this.consumerMessaging = consumerMessaging;
        this.channelManager = channelManager;
    }

    @Override
    public ChannelPage channels(io.casehub.qhorus.api.channel.ChannelQuery query) {
        int offset = query != null && query.offset() != null ? query.offset() : 0;
        int limit = query != null && query.limit() != null ? query.limit() : 20;

        ChannelQuery.Builder queryBuilder = ChannelQuery.builder();
        if (query != null) {
            if (query.keyword() != null) queryBuilder.keyword(query.keyword());
            if (query.namePrefix() != null) queryBuilder.namePrefix(query.namePrefix());
            if (query.semantic() != null) queryBuilder.semantic(query.semantic());
            if (query.paused() != null) queryBuilder.paused(query.paused());
            if (query.spaceId() != null) queryBuilder.spaceId(query.spaceId());
        }

        List<Channel> all = channelReader.scan(queryBuilder.build());
        int total = all.size();
        int end = Math.min(offset + limit, total);
        List<Channel> items = offset < total
                ? all.subList(offset, end)
                : List.of();

        boolean hasNext = end < total;
        return new ChannelPage(items, hasNext, null);
    }

    @Override
    public Channel channel(UUID id, String name) {
        if (id != null) {
            return channelReader.findById(id).orElse(null);
        }
        if (name != null) {
            return channelReader.findByName(name).orElse(null);
        }
        throw new IllegalArgumentException("Either id or name must be provided");
    }

    @Override
    public List<Message> channelMessages(UUID channelId, Long afterId, Integer limit) {
        long cursor = afterId != null ? afterId : 0;
        int maxMessages = limit != null ? limit : 50;
        return consumerMessaging.history(channelId, cursor, maxMessages);
    }

    @Override
    public Channel createChannel(ChannelCreateRequest input) {
        return channelManager.create(input);
    }

    @Override
    public long deleteChannel(UUID channelId, Boolean force) {
        return channelManager.delete(channelId, force != null && force);
    }

    @Override
    public Channel pauseChannel(UUID channelId) {
        return channelManager.pause(channelId);
    }

    @Override
    public Channel resumeChannel(UUID channelId) {
        return channelManager.resume(channelId);
    }
}
