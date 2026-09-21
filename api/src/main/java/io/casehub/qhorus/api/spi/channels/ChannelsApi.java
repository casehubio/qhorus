package io.casehub.qhorus.api.spi.channels;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.qhorus.api.channel.BackendInfo;
import io.casehub.qhorus.api.channel.CapacityThresholdConfig;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelEnforcementConfig;
import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.channel.ChannelPage;
import io.casehub.qhorus.api.channel.ChannelProtocolConfig;
import io.casehub.qhorus.api.channel.ChannelQuery;
import io.casehub.qhorus.api.channel.ChannelRoutingConfig;
import io.casehub.qhorus.api.channel.ChannelSummaryResult;
import io.casehub.qhorus.api.channel.ClearChannelResult;
import io.casehub.qhorus.api.channel.DeleteSpaceResult;
import io.casehub.qhorus.api.channel.ForceReleaseResult;
import io.casehub.qhorus.api.channel.MessageDeliveryStatus;
import io.casehub.qhorus.api.channel.RoutingDiagnostic;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.channel.SpaceCreateRequest;
import io.casehub.qhorus.api.channel.TopicMergeResult;
import io.casehub.qhorus.api.channel.TypeConstraintsRequest;
import io.casehub.qhorus.api.channel.TopicMoveResult;
import io.casehub.qhorus.api.channel.TopicRenameResult;
import io.casehub.qhorus.api.channel.UnreadCount;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.Topic;
import io.casehub.qhorus.api.message.TopicSummary;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@McpDomain("channels")
public interface ChannelsApi {

    // --- Channel queries ---

    @PlatformQuery("List channels matching filter criteria")
    ChannelPage channels(ChannelQuery query);

    @PlatformQuery("Get a channel by ID or name")
    Channel channel(UUID id, String name);

    @PlatformQuery("Get messages in a channel")
    List<Message> channelMessages(UUID channelId, Long afterId, Integer limit);

    // --- Channel mutations ---

    @PlatformMutation("Create a new channel")
    Channel createChannel(ChannelCreateRequest input);

    @PlatformMutation("Delete a channel")
    long deleteChannel(UUID channelId, Boolean force);

    @PlatformMutation("Pause a channel")
    Channel pauseChannel(UUID channelId);

    @PlatformMutation("Resume a paused channel")
    Channel resumeChannel(UUID channelId);

    @PlatformMutation("Clear all non-event messages from a channel")
    ClearChannelResult clearChannel(UUID channelId);

    // --- Channel config mutations ---

    @PlatformMutation("Update channel rate limits")
    Channel setChannelRateLimits(UUID channelId, Integer perChannel, Integer perInstance);

    @PlatformMutation("Set delivery tracking on a channel")
    Channel setDeliveryTracking(UUID channelId, Boolean tracking);

    @PlatformMutation("Set channel write ACL")
    Channel setChannelWriters(UUID channelId, List<String> allowedWriters);

    @PlatformMutation("Set channel admin instances")
    Channel setChannelAdmins(UUID channelId, List<String> adminInstances);

    @PlatformMutation("Set channel reviewer instances")
    Channel setChannelReviewers(UUID channelId, List<String> reviewerInstances);

    @PlatformMutation("Set channel message type constraints")
    Channel setChannelTypeConstraints(UUID channelId, TypeConstraintsRequest constraints);

    // --- Protocol ---

    @PlatformQuery("List all registered channel protocol names")
    List<String> protocols();

    @PlatformQuery("Get protocol configuration for a channel")
    ChannelProtocolConfig channelProtocols(UUID channelId);

    @PlatformMutation("Set channel protocols")
    Channel setChannelProtocols(UUID channelId, List<String> protocols);

    @PlatformMutation("Set protocol participants for a channel")
    Channel setProtocolParticipants(UUID channelId, List<String> participants);

    // --- Enforcement ---

    @PlatformQuery("Get enforcement configuration for a channel")
    ChannelEnforcementConfig channelEnforcement(UUID channelId);

    @PlatformMutation("Set enforcement mode on a channel")
    Channel setEnforcementMode(UUID channelId, String mode);

    @PlatformMutation("Set enforcement exclusions for a channel")
    Channel setEnforcementExclusions(UUID channelId, List<String> exclusions);

    // --- Routing ---

    @PlatformQuery("Get routing configuration for a channel")
    ChannelRoutingConfig routingConfig(UUID channelId);

    @PlatformQuery("Diagnostic: show routing candidates for a capability")
    RoutingDiagnostic routingCandidates(String capability, UUID channelId);

    @PlatformMutation("Set routing trust threshold for a channel")
    Channel setRoutingConfig(UUID channelId, Double trustThreshold);

    // --- Summary ---

    @PlatformQuery("Get maintained summary for a channel")
    ChannelSummaryResult channelSummary(UUID channelId);

    @PlatformMutation("Set or update a channel's summary text")
    ChannelSummaryResult updateChannelSummary(UUID channelId, String summary);

    @PlatformMutation("Configure auto-update thresholds for channel summary")
    ChannelSummaryResult configureChannelSummary(UUID channelId, Integer updateAfterMessages, Integer updateAfterSeconds);

    @PlatformMutation("Trigger an immediate summary update via the configured hook")
    ChannelSummaryResult triggerChannelSummaryUpdate(UUID channelId);

    // --- Projection ---

    @PlatformQuery("List all registered projection names")
    List<String> projections();

    @PlatformQuery("Project a channel's message history through a named projection")
    String projectChannel(UUID channelId, String projectionName, Integer maxMessages, String topic);

    // --- Capacity thresholds ---

    @PlatformQuery("Get per-channel redistribution capacity threshold")
    CapacityThresholdConfig redistributionThreshold(UUID channelId);

    @PlatformQuery("Get per-channel routing capacity threshold")
    CapacityThresholdConfig routingCapacityThreshold(UUID channelId);

    @PlatformMutation("Set per-channel redistribution capacity threshold")
    Channel setRedistributionThreshold(UUID channelId, Double threshold);

    @PlatformMutation("Set per-channel routing capacity threshold")
    Channel setRoutingCapacityThreshold(UUID channelId, Double threshold);

    // --- Topic queries ---

    @PlatformQuery("List all topics in a channel with message counts and activity timestamps")
    List<TopicSummary> topics(UUID channelId);

    // --- Topic mutations ---

    @PlatformMutation("Mark a topic as resolved")
    Topic resolveTopic(UUID channelId, String topicName, String actorId);

    @PlatformMutation("Unresolve a previously resolved topic")
    Topic unresolveTopic(UUID channelId, String topicName);

    @PlatformMutation("Rename a topic and update all messages")
    TopicRenameResult renameTopic(UUID channelId, String oldName, String newName, String actorId);

    @PlatformMutation("Merge a source topic into a target topic")
    TopicMergeResult mergeTopics(UUID channelId, String sourceTopic, String targetTopic, String actorId);

    @PlatformMutation("Move all messages in a topic from one channel to another")
    TopicMoveResult moveTopic(UUID sourceChannelId, String topicName, UUID targetChannelId, String actorId);

    // --- Membership queries ---

    @PlatformQuery("List all members of a channel with their roles")
    List<ChannelMembership> members(UUID channelId);

    @PlatformQuery("Get unread message counts across all channels for a member")
    List<UnreadCount> unreadCounts(String memberId);

    @PlatformQuery("Check which channel members have received a specific message")
    List<MessageDeliveryStatus> messageDeliveryStatus(UUID channelId, Long messageId);

    // --- Membership mutations ---

    @PlatformMutation("Join a channel as a member with a specified role")
    ChannelMembership joinChannel(UUID channelId, String memberId, String role);

    @PlatformMutation("Leave a channel, removing membership")
    void leaveChannel(UUID channelId, String memberId);

    @PlatformMutation("Mark a channel as read up to a specific message ID")
    void markChannelRead(UUID channelId, String memberId, Long messageId);

    // --- Space queries ---

    @PlatformQuery("Get a space by ID")
    Space space(UUID id);

    @PlatformQuery("List spaces, optionally filtered by parent")
    List<Space> spaces(UUID parentSpaceId);

    @PlatformQuery("List all channels belonging to a space")
    List<Channel> spaceChannels(UUID spaceId);

    // --- Space mutations ---

    @PlatformMutation("Create an organizational space to group related channels")
    Space createSpace(SpaceCreateRequest request);

    @PlatformMutation("Delete a space")
    DeleteSpaceResult deleteSpace(UUID spaceId);

    @PlatformMutation("Rename a space")
    Space renameSpace(UUID spaceId, String newName);

    @PlatformMutation("Update a space's description")
    Space updateSpaceDescription(UUID spaceId, String description);

    @PlatformMutation("Move a space to a new parent")
    Space moveSpace(UUID spaceId, UUID newParentSpaceId);

    @PlatformMutation("Move a channel into a space")
    Channel moveChannelToSpace(UUID channelId, UUID spaceId);

    // --- Gateway queries ---

    @PlatformQuery("List registered channel backends for a channel")
    List<BackendInfo> backends(UUID channelId);

    // --- Gateway mutations ---

    @PlatformMutation("Register a channel backend")
    void registerBackend(UUID channelId, String backendId, String backendType);

    @PlatformMutation("Remove a registered backend from a channel")
    void deregisterBackend(UUID channelId, String backendId);

    // --- Force release ---

    @PlatformMutation("Force-release a BARRIER or COLLECT channel")
    ForceReleaseResult forceReleaseChannel(UUID channelId, String reason, String callerInstanceId);
}
