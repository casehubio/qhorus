package io.casehub.qhorus.api.spi.messaging;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.qhorus.api.message.CancelWaitResult;
import io.casehub.qhorus.api.message.DeleteMessageResult;
import io.casehub.qhorus.api.message.DispatchMessageRequest;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageReactions;
import io.casehub.qhorus.api.message.Reaction;
import io.casehub.qhorus.api.message.ReactionGroup;
import io.casehub.qhorus.api.message.WaitResult;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "messaging", app = "qhorus")
public interface MessagingApi {

    @PlatformQuery("Get a message by ID")
    Message message(Long id);

    @PlatformQuery("Get replies to a message")
    List<Message> replies(Long messageId, Long afterId, Integer limit);

    @PlatformQuery("Search messages")
    List<Message> searchMessages(String query, UUID channelId, Integer limit);

    @PlatformQuery("Get reactions for a message")
    List<ReactionGroup> reactions(Long messageId);

    @PlatformQuery("Get reactions for multiple messages")
    List<MessageReactions> reactionsBatch(List<Long> messageIds);

    @PlatformMutation("Dispatch a message")
    DispatchResult dispatchMessage(DispatchMessageRequest input);

    @PlatformMutation("Delete a message")
    DeleteMessageResult deleteMessage(Long messageId);

    @PlatformMutation("Add a reaction")
    Reaction react(Long messageId, String emoji);

    @PlatformMutation("Remove a reaction")
    boolean unreact(Long messageId, String emoji);

    @PlatformMutation("Respond to an approval request")
    DispatchResult respondToApproval(String correlationId, String responseText, UUID channelId);

    @PlatformMutation("Cancel a wait")
    CancelWaitResult cancelWait(String correlationId);

    @PlatformMutation("Wait for a reply in a channel")
    WaitResult waitForReply(UUID channelId, String correlationId, Integer timeoutSeconds);

    @PlatformMutation("Request approval in a channel")
    WaitResult requestApproval(UUID channelId, String content, Integer timeoutSeconds);
}
