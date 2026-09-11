package io.casehub.qhorus.graphql.messaging;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.qhorus.api.channel.ReactionManager;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.Reaction;
import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.graphql.dto.CancelWaitResultType;
import io.casehub.qhorus.graphql.dto.DeleteMessageResultType;
import io.casehub.qhorus.graphql.dto.DispatchMessageInput;
import io.casehub.qhorus.graphql.dto.DispatchResultType;
import io.casehub.qhorus.graphql.dto.ReactionType;
import io.casehub.qhorus.graphql.dto.WaitResultType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;

@GraphQLApi
@McpDomain("messaging")
@ApplicationScoped
public class MessagingMutationResolver {

    @Inject MessageDispatcher messageDispatcher;
    @Inject CurrentPrincipal currentPrincipal;
    @Inject ReactionManager reactionManager;
    @Inject MessageStore messageStore;
    @Inject ConsumerMessaging consumerMessaging;
    @Inject CommitmentStore commitmentStore;

    @Mutation
    @Description("Dispatch a typed message to a channel")
    public DispatchResultType dispatchMessage(DispatchMessageInput input) {
        String actorId = currentPrincipal.actorId();
        String tenancyId = currentPrincipal.tenancyId();

        MessageDispatch.Builder builder = MessageDispatch.builder()
                .channelId(input.channelId())
                .sender(actorId)
                .type(MessageType.valueOf(input.type()))
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

    @Mutation
    @Description("Delete a single message by its sequence ID. Orphans replies (sets inReplyTo to null).")
    @jakarta.transaction.Transactional
    public DeleteMessageResultType deleteMessage(Long messageId) {
        Message msg = messageStore.find(messageId).orElse(null);
        if (msg == null) {
            return new DeleteMessageResultType(messageId, false, null, null, null,
                    "Message not found: " + messageId);
        }
        String sender = msg.sender();
        String type = msg.messageType().name();
        String preview = msg.content() != null
                ? (msg.content().length() > 80 ? msg.content().substring(0, 80) + "…" : msg.content())
                : null;
        messageStore.scan(MessageQuery.builder().inReplyTo(messageId).build())
                .forEach(reply -> messageStore.put(reply.toBuilder().inReplyTo(null).build()));
        messageDispatcher.dispatch(MessageDispatch.builder()
                .channelId(msg.channelId()).sender("system").type(MessageType.EVENT)
                .actorType(ActorType.SYSTEM).build());
        messageStore.delete(msg.id());
        return new DeleteMessageResultType(messageId, true, sender, type, preview,
                "Message " + messageId + " deleted");
    }

    @Mutation
    @Description("Add an emoji reaction to a message. Idempotent.")
    public ReactionType react(Long messageId, String emoji) {
        Reaction r = reactionManager.react(messageId, emoji);
        return ReactionType.from(r);
    }

    @Mutation
    @Description("Remove an emoji reaction from a message. Idempotent.")
    public boolean unreact(Long messageId, String emoji) {
        return reactionManager.unreact(messageId, emoji);
    }

    @Mutation
    @Description("Send a response to a pending approval request identified by correlationId")
    public DispatchResultType respondToApproval(String correlationId, String responseText, UUID channelId) {
        Long inReplyTo = consumerMessaging.findByCorrelationId(correlationId)
                .map(Message::id)
                .orElse(null);
        MessageDispatch dispatch = new MessageDispatch(
                channelId, "human", MessageType.RESPONSE,
                responseText, null, correlationId, inReplyTo, null, null, null, null,
                ActorType.HUMAN, null, null, null, null);
        DispatchResult result = messageDispatcher.dispatch(dispatch);
        return DispatchResultType.from(result);
    }

    @Mutation
    @Description("Cancel a pending wait by its correlationId. The waiting caller receives a cancelled status.")
    public CancelWaitResultType cancelWait(String correlationId) {
        Optional<Commitment> opt = commitmentStore.findByCorrelationId(correlationId);
        if (opt.isPresent()) {
            commitmentStore.deleteById(opt.get().id());
            return new CancelWaitResultType(correlationId, true,
                    "Cancelled pending wait for correlation_id=" + correlationId);
        }
        return new CancelWaitResultType(correlationId, false,
                "No pending wait found for correlation_id=" + correlationId);
    }

    @Mutation
    @Description("Block until a response arrives for the given correlationId, or until timeout. "
            + "Returns immediately if a matching response already exists.")
    public WaitResultType waitForReply(UUID channelId, String correlationId, Integer timeoutSeconds) {
        int timeout = timeoutSeconds != null ? timeoutSeconds : 90;
        Instant expiresAt = Instant.now().plusSeconds(timeout);

        long pollMs = 100;
        while (Instant.now().isBefore(expiresAt)) {
            Optional<Commitment> opt = commitmentStore.findByCorrelationId(correlationId);
            if (opt.isEmpty()) {
                return new WaitResultType(false, false, correlationId, null,
                        "Wait cancelled for correlation_id=" + correlationId);
            }
            Commitment commitment = opt.get();
            if (commitment.state() == CommitmentState.OPEN
                    || commitment.state() == CommitmentState.ACKNOWLEDGED
                    || commitment.state() == CommitmentState.FULFILLED
                    || commitment.state() == CommitmentState.DELEGATED) {
                Message response = findTerminalMessage(channelId, correlationId, MessageType.RESPONSE);
                if (response != null) {
                    return new WaitResultType(true, false, correlationId,
                            io.casehub.qhorus.graphql.dto.MessageType.from(response),
                            "Response received for correlation_id=" + correlationId);
                }
                Message done = findTerminalMessage(channelId, correlationId, MessageType.DONE);
                if (done != null) {
                    return new WaitResultType(true, false, correlationId,
                            io.casehub.qhorus.graphql.dto.MessageType.from(done),
                            "Done received for correlation_id=" + correlationId);
                }
            }
            if (commitment.state() == CommitmentState.DECLINED) {
                return new WaitResultType(false, false, correlationId, null,
                        "Request was DECLINED for correlation_id=" + correlationId);
            }
            if (commitment.state() == CommitmentState.FAILED) {
                return new WaitResultType(false, false, correlationId, null,
                        "Request FAILED for correlation_id=" + correlationId);
            }
            if (commitment.state() == CommitmentState.EXPIRED) {
                return new WaitResultType(false, true, correlationId, null,
                        "Commitment EXPIRED for correlation_id=" + correlationId);
            }
            try {
                Thread.sleep(pollMs);
                pollMs = Math.min(pollMs * 2, 500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return new WaitResultType(false, true, correlationId, null,
                "Timed out after " + timeout + "s waiting for response to correlation_id=" + correlationId);
    }

    @Mutation
    @Description("Send an approval request to a channel and block until a human responds or timeout. "
            + "Dispatches a QUERY and then waits for a RESPONSE.")
    public WaitResultType requestApproval(UUID channelId, String content, Integer timeoutSeconds) {
        String correlationId = UUID.randomUUID().toString();
        int timeout = timeoutSeconds != null ? timeoutSeconds : 300;
        String actorId = currentPrincipal.actorId();
        String tenancyId = currentPrincipal.tenancyId();

        messageDispatcher.dispatch(MessageDispatch.builder()
                .channelId(channelId)
                .sender(actorId)
                .type(MessageType.QUERY)
                .content(content)
                .correlationId(correlationId)
                .actorType(ActorType.HUMAN)
                .tenancyId(tenancyId)
                .build());

        return waitForReply(channelId, correlationId, timeout);
    }

    private Message findTerminalMessage(UUID channelId, String correlationId, MessageType type) {
        List<Message> results = messageStore.scan(MessageQuery.builder()
                .channelId(channelId)
                .correlationId(correlationId)
                .messageType(type)
                .limit(1)
                .build());
        return results.isEmpty() ? null : results.getFirst();
    }
}
