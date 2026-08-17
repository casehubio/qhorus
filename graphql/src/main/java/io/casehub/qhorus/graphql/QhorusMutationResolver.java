package io.casehub.qhorus.graphql;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.graphql.dto.ChannelType;
import io.casehub.qhorus.graphql.dto.CreateChannelInput;
import io.casehub.qhorus.graphql.dto.DispatchMessageInput;
import io.casehub.qhorus.graphql.dto.DispatchResultType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;

@GraphQLApi
@McpDomain("qhorus")
@ApplicationScoped
public class QhorusMutationResolver {

    @Inject ChannelManager channelManager;
    @Inject MessageDispatcher messageDispatcher;
    @Inject CurrentPrincipal currentPrincipal;

    @Mutation
    @Description("Create a new communication channel")
    public ChannelType createChannel(CreateChannelInput input) {
        ChannelCreateRequest.Builder builder = ChannelCreateRequest.builder(input.name());
        if (input.description() != null) builder.description(input.description());
        if (input.semantic() != null) builder.semantic(input.semantic());
        if (input.barrierContributors() != null) builder.barrierContributors(input.barrierContributors());
        if (input.allowedWriters() != null) builder.allowedWriters(input.allowedWriters());
        if (input.adminInstances() != null) builder.adminInstances(input.adminInstances());
        if (input.rateLimitPerChannel() != null) builder.rateLimitPerChannel(input.rateLimitPerChannel());
        if (input.rateLimitPerInstance() != null) builder.rateLimitPerInstance(input.rateLimitPerInstance());
        if (input.spaceId() != null) builder.spaceId(input.spaceId());
        if (input.protocols() != null) builder.protocols(input.protocols());

        Channel channel = channelManager.create(builder.build());
        return ChannelType.from(channel);
    }

    @Mutation
    @Description("Delete a channel — optionally force-delete even if it has messages")
    public long deleteChannel(UUID channelId, Boolean force) {
        return channelManager.delete(channelId, force != null && force);
    }

    @Mutation
    @Description("Pause a channel — blocks new message dispatch")
    public ChannelType pauseChannel(UUID channelId) {
        Channel channel = channelManager.pause(channelId);
        return ChannelType.from(channel);
    }

    @Mutation
    @Description("Resume a paused channel — allows message dispatch again")
    public ChannelType resumeChannel(UUID channelId) {
        Channel channel = channelManager.resume(channelId);
        return ChannelType.from(channel);
    }

    @Mutation
    @Description("Dispatch a typed message to a channel")
    public DispatchResultType dispatchMessage(DispatchMessageInput input) {
        String actorId = currentPrincipal.actorId();
        String tenancyId = currentPrincipal.tenancyId();

        MessageDispatch.Builder builder = MessageDispatch.builder()
                .channelId(input.channelId())
                .sender(actorId)
                .type(io.casehub.qhorus.api.message.MessageType.valueOf(input.type()))
                .actorType(ActorType.HUMAN)
                .tenancyId(tenancyId);

        if (input.content() != null) builder.content(input.content());
        if (input.correlationId() != null) builder.correlationId(input.correlationId());
        if (input.inReplyTo() != null) builder.inReplyTo(input.inReplyTo());
        if (input.target() != null) builder.target(input.target());
        if (input.topic() != null) builder.topic(input.topic());
        if (input.deadline() != null) builder.deadline(input.deadline());

        DispatchResult result = messageDispatcher.dispatch(builder.build());
        return DispatchResultType.from(result);
    }
}
