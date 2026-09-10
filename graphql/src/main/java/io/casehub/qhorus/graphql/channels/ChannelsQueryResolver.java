package io.casehub.qhorus.graphql.channels;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.graphql.PageInfo;
import io.casehub.platform.graphql.PageInput;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.store.query.ChannelQuery;
import io.casehub.qhorus.graphql.dto.ChannelFilterInput;
import io.casehub.qhorus.graphql.dto.ChannelPage;
import io.casehub.qhorus.graphql.dto.ChannelType;
import io.casehub.qhorus.graphql.dto.MessageType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Query;

@GraphQLApi
@McpDomain("channels")
@ApplicationScoped
public class ChannelsQueryResolver {

    @Inject ChannelReader channelReader;
    @Inject ConsumerMessaging consumerMessaging;

    @Query
    @Description("List channels with optional filtering and pagination")
    public ChannelPage channels(ChannelFilterInput filter, PageInput page) {
        int offset = page != null && page.offset() != null ? page.offset() : 0;
        int limit = page != null && page.limit() != null ? page.limit() : 20;

        ChannelQuery.Builder queryBuilder = ChannelQuery.builder();
        if (filter != null) {
            if (filter.keyword() != null) queryBuilder.keyword(filter.keyword());
            if (filter.namePrefix() != null) queryBuilder.namePrefix(filter.namePrefix());
            if (filter.semantic() != null) queryBuilder.semantic(filter.semantic());
            if (filter.paused() != null) queryBuilder.paused(filter.paused());
            if (filter.spaceId() != null) queryBuilder.spaceId(filter.spaceId());
        }

        List<Channel> all = channelReader.scan(queryBuilder.build());
        int total = all.size();
        int end = Math.min(offset + limit, total);
        List<ChannelType> items = offset < total
                ? all.subList(offset, end).stream().map(ChannelType::from).toList()
                : List.of();

        boolean hasNext = end < total;
        boolean hasPrevious = offset > 0;
        return new ChannelPage(items, new PageInfo(hasNext, hasPrevious, total, null));
    }

    @Query
    @Description("Retrieve a single channel by ID or name")
    public ChannelType channel(UUID id, String name) {
        if (id != null) {
            return channelReader.findById(id).map(ChannelType::from).orElse(null);
        }
        if (name != null) {
            return channelReader.findByName(name).map(ChannelType::from).orElse(null);
        }
        throw new IllegalArgumentException("Either id or name must be provided");
    }

    @Query
    @Description("Retrieve message history for a channel — cursor-based with afterId")
    public List<MessageType> channelMessages(UUID channelId, Long afterId, Integer limit) {
        long cursor = afterId != null ? afterId : 0;
        int maxMessages = limit != null ? limit : 50;

        List<Message> messages = consumerMessaging.history(channelId, cursor, maxMessages);
        return messages.stream().map(MessageType::from).toList();
    }
}
