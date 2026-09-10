package io.casehub.qhorus.graphql.messaging;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.graphql.dto.DispatchMessageInput;
import io.casehub.qhorus.graphql.dto.DispatchResultType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;

@GraphQLApi
@McpDomain("messaging")
@ApplicationScoped
public class MessagingMutationResolver {

    @Inject MessageDispatcher messageDispatcher;
    @Inject CurrentPrincipal currentPrincipal;

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
