package io.casehub.qhorus.graphql.messaging;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.Reaction;
import io.casehub.qhorus.api.store.MessageReader;
import io.casehub.qhorus.api.store.ReactionReader;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.graphql.dto.MessageReactionsType;
import io.casehub.qhorus.graphql.dto.ReactionGroupType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Query;

@GraphQLApi
@McpDomain("messaging")
@ApplicationScoped
public class MessagingQueryResolver {

    @Inject ConsumerMessaging consumerMessaging;
    @Inject MessageReader messageReader;
    @Inject ReactionReader reactionReader;

    @Query
    @Description("Look up a message by its numeric ID")
    public io.casehub.qhorus.graphql.dto.MessageType message(Long id) {
        Message msg = consumerMessaging.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + id));
        return io.casehub.qhorus.graphql.dto.MessageType.from(msg);
    }

    @Query
    @Description("Retrieve direct replies to a specific message with cursor pagination")
    public List<io.casehub.qhorus.graphql.dto.MessageType> replies(Long messageId, Long afterId, Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 100) : 20;
        MessageQuery.Builder builder = MessageQuery.builder().inReplyTo(messageId).limit(effectiveLimit);
        if (afterId != null) builder.afterId(afterId);
        return messageReader.scan(builder.build()).stream()
                .map(io.casehub.qhorus.graphql.dto.MessageType::from)
                .toList();
    }

    @Query
    @Description("Full-text keyword search across messages, excludes EVENT type")
    public List<io.casehub.qhorus.graphql.dto.MessageType> searchMessages(String query, UUID channelId, Integer limit) {
        int pageSize = (limit != null && limit > 0) ? limit : 20;
        MessageQuery.Builder builder = MessageQuery.builder()
                .contentPattern(query)
                .excludeTypes(List.of(MessageType.EVENT))
                .limit(pageSize);
        if (channelId != null) builder.channelId(channelId);
        return messageReader.scan(builder.build()).stream()
                .map(io.casehub.qhorus.graphql.dto.MessageType::from)
                .toList();
    }

    @Query
    @Description("Get all reactions for a message, grouped by emoji with actor lists")
    public List<ReactionGroupType> reactions(Long messageId) {
        return groupReactions(reactionReader.findByMessage(messageId));
    }

    @Query
    @Description("Get reactions for multiple messages in one call, grouped by emoji per message")
    public List<MessageReactionsType> reactionsBatch(List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            throw new IllegalArgumentException("messageIds must be non-null and non-empty");
        }
        if (messageIds.size() > 200) {
            throw new IllegalArgumentException("messageIds cannot exceed 200 entries");
        }
        Map<Long, List<Reaction>> byMessage = reactionReader.findByMessages(messageIds);
        return messageIds.stream()
                .map(id -> new MessageReactionsType(id,
                        groupReactions(byMessage.getOrDefault(id, List.of()))))
                .toList();
    }

    private List<ReactionGroupType> groupReactions(Collection<Reaction> reactions) {
        return reactions.stream()
                .collect(Collectors.groupingBy(Reaction::emoji))
                .entrySet().stream()
                .map(e -> new ReactionGroupType(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().map(Reaction::actorId).toList()))
                .toList();
    }
}
