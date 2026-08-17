package io.casehub.qhorus.graphql;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.qhorus.graphql.dto.MessageType;
import io.casehub.qhorus.graphql.dto.PresenceType;
import io.smallrye.graphql.api.Subscription;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;

@GraphQLApi
@McpDomain("qhorus")
@ApplicationScoped
public class QhorusSubscriptionResolver {

    private final QhorusEventPublisher publisher;

    @Inject
    public QhorusSubscriptionResolver(QhorusEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Subscription
    @Description("Live channel activity — new messages dispatched to a channel")
    public Multi<MessageType> channelActivity(@Name("channelId") UUID channelId) {
        return publisher.messageStream()
                .filter(event -> event.channelId().equals(channelId))
                .map(event -> new MessageType(
                        event.messageId(),
                        event.channelId(),
                        event.senderId(),
                        event.messageType() != null ? event.messageType().name() : null,
                        event.actorType() != null ? event.actorType().name() : null,
                        event.content(),
                        event.correlationId(),
                        null,
                        0,
                        event.target(),
                        event.topic(),
                        null,
                        event.occurredAt()));
    }

    @Subscription
    @Description("Live presence changes — member status transitions")
    public Multi<PresenceType> channelPresence(@Name("channelId") UUID channelId) {
        return publisher.presenceStream()
                .filter(event -> channelId == null || channelId.equals(event.channelId()))
                .map(PresenceType::fromEvent);
    }
}
