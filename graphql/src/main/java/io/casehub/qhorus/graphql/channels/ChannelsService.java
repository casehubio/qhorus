package io.casehub.qhorus.graphql.channels;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.BackendInfo;
import io.casehub.qhorus.api.channel.CapacityThresholdConfig;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelEnforcementConfig;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.channel.ChannelPage;
import io.casehub.qhorus.api.channel.ChannelProtocolConfig;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.ChannelRoutingConfig;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.ChannelSummary;
import io.casehub.qhorus.api.channel.ChannelSummaryManager;
import io.casehub.qhorus.api.channel.ChannelSummaryResult;
import io.casehub.qhorus.api.channel.ClearChannelResult;
import io.casehub.qhorus.api.channel.DeleteSpaceResult;
import io.casehub.qhorus.api.channel.EnforcementMode;
import io.casehub.qhorus.api.channel.ForceReleaseResult;
import io.casehub.qhorus.api.channel.MemberRole;
import io.casehub.qhorus.api.channel.MembershipManager;
import io.casehub.qhorus.api.channel.MessageDeliveryStatus;
import io.casehub.qhorus.api.channel.ProjectionReader;
import io.casehub.qhorus.api.channel.ProtocolReader;
import io.casehub.qhorus.api.channel.RoutingDiagnostic;
import io.casehub.qhorus.api.channel.RoutingDiagnostics;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.channel.SpaceCreateRequest;
import io.casehub.qhorus.api.channel.SpaceManager;
import io.casehub.qhorus.api.channel.TopicManager;
import io.casehub.qhorus.api.channel.TopicMergeResult;
import io.casehub.qhorus.api.channel.TopicMoveResult;
import io.casehub.qhorus.api.channel.TopicRenameResult;
import io.casehub.qhorus.api.channel.UnreadCount;
import io.casehub.qhorus.api.channel.UnreadCountProvider;
import io.casehub.qhorus.api.gateway.BackendRegistry;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.Topic;
import io.casehub.qhorus.api.message.TopicSummary;
import io.casehub.qhorus.api.spi.channels.ChannelsApi;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.ChannelQuery;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ChannelsService implements ChannelsApi {

    private final ChannelReader         channelReader;
    private final ConsumerMessaging     consumerMessaging;
    private final ChannelManager        channelManager;
    private final TopicManager          topicManager;
    private final MembershipManager     membershipManager;
    private final UnreadCountProvider   unreadCountProvider;
    private final SpaceManager          spaceManager;
    private final BackendRegistry       backendRegistry;
    private final MessageStore          messageStore;
    private final CurrentPrincipal      currentPrincipal;
    private final ChannelSummaryManager channelSummaryManager;
    private final ProjectionReader      projectionReader;
    private final ProtocolReader        protocolReader;
    private final RoutingDiagnostics    routingDiagnostics;

    public ChannelsService(ChannelReader channelReader,
                           ConsumerMessaging consumerMessaging,
                           ChannelManager channelManager,
                           TopicManager topicManager,
                           MembershipManager membershipManager,
                           UnreadCountProvider unreadCountProvider,
                           SpaceManager spaceManager,
                           BackendRegistry backendRegistry,
                           MessageStore messageStore,
                           CurrentPrincipal currentPrincipal,
                           ChannelSummaryManager channelSummaryManager,
                           ProjectionReader projectionReader,
                           ProtocolReader protocolReader,
                           RoutingDiagnostics routingDiagnostics) {
        this.channelReader         = channelReader;
        this.consumerMessaging     = consumerMessaging;
        this.channelManager        = channelManager;
        this.topicManager          = topicManager;
        this.membershipManager     = membershipManager;
        this.unreadCountProvider   = unreadCountProvider;
        this.spaceManager          = spaceManager;
        this.backendRegistry       = backendRegistry;
        this.messageStore          = messageStore;
        this.currentPrincipal      = currentPrincipal;
        this.channelSummaryManager = channelSummaryManager;
        this.projectionReader      = projectionReader;
        this.protocolReader        = protocolReader;
        this.routingDiagnostics    = routingDiagnostics;
    }

    // --- Channel queries ---

    @Override
    public ChannelPage channels(io.casehub.qhorus.api.channel.ChannelQuery query) {
        int offset = query != null && query.offset() != null ? query.offset() : 0;
        int limit  = query != null && query.limit() != null ? query.limit() : 20;

        ChannelQuery.Builder queryBuilder = ChannelQuery.builder();
        if (query != null) {
            if (query.keyword() != null) {queryBuilder.keyword(query.keyword());}
            if (query.namePrefix() != null) {queryBuilder.namePrefix(query.namePrefix());}
            if (query.semantic() != null) {queryBuilder.semantic(query.semantic());}
            if (query.paused() != null) {queryBuilder.paused(query.paused());}
            if (query.spaceId() != null) {queryBuilder.spaceId(query.spaceId());}
        }

        List<Channel> all   = channelReader.scan(queryBuilder.build());
        int           total = all.size();
        int           end   = Math.min(offset + limit, total);
        List<Channel> items = offset < total ? all.subList(offset, end) : List.of();

        boolean hasNext = end < total;
        return new ChannelPage(items, hasNext, null);
    }

    @Override
    public Channel channel(UUID id, String name) {
        if (id != null) {return channelReader.findById(id).orElse(null);}
        if (name != null) {return channelReader.findByName(name).orElse(null);}
        throw new IllegalArgumentException("Either id or name must be provided");
    }

    @Override
    public List<Message> channelMessages(UUID channelId, Long afterId, Integer limit) {
        long cursor      = afterId != null ? afterId : 0;
        int  maxMessages = limit != null ? limit : 50;
        return consumerMessaging.history(channelId, cursor, maxMessages);
    }

    // --- Channel mutations ---

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

    @Override
    public ClearChannelResult clearChannel(UUID channelId) {
        Channel ch = channelReader.findById(channelId)
                                  .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        List<Message> messages = messageStore.scan(
                io.casehub.qhorus.api.store.query.MessageQuery.builder()
                                                              .channelId(ch.id())
                                                              .excludeTypes(List.of(io.casehub.qhorus.api.message.MessageType.EVENT))
                                                              .build());
        int count = messages.size();
        messageStore.deleteNonEvent(ch.id());
        channelManager.updateLastActivity(ch.id(), ch.tenancyId());
        return new ClearChannelResult(ch.name(), count, true);
    }

    // --- Channel config mutations ---

    @Override
    public Channel setChannelRateLimits(UUID channelId, Integer perChannel, Integer perInstance) {
        return channelManager.setRateLimits(channelId, perChannel, perInstance);
    }

    @Override
    public Channel setDeliveryTracking(UUID channelId, Boolean tracking) {
        channelManager.setTrackDelivery(channelId, tracking);
        return channelReader.findById(channelId).orElseThrow();
    }

    @Override
    public Channel setChannelWriters(UUID channelId, List<String> allowedWriters) {
        return channelManager.setAllowedWriters(channelId, allowedWriters);
    }

    @Override
    public Channel setChannelAdmins(UUID channelId, List<String> adminInstances) {
        return channelManager.setAdminInstances(channelId, adminInstances);
    }

    @Override
    public Channel setChannelReviewers(UUID channelId, List<String> reviewerInstances) {
        return channelManager.setReviewerInstances(channelId, reviewerInstances);
    }

    @Override
    public Channel setChannelTypeConstraints(UUID channelId,
                                             io.casehub.qhorus.api.channel.TypeConstraintsRequest constraints) {
        return channelManager.setTypeConstraints(channelId,
                                                 constraints != null ? constraints.allowedTypes() : null,
                                                 constraints != null ? constraints.deniedTypes() : null);
    }

    // --- Protocol ---

    @Override
    public List<String> protocols() {
        return protocolReader.registeredProtocols();
    }

    @Override
    public ChannelProtocolConfig channelProtocols(UUID channelId) {
        Channel ch = channelReader.findById(channelId)
                                  .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        return new ChannelProtocolConfig(ch.protocols(), ch.protocolParticipants());
    }

    @Override
    public Channel setChannelProtocols(UUID channelId, List<String> protocols) {
        return channelManager.setProtocols(channelId, protocols);
    }

    @Override
    public Channel setProtocolParticipants(UUID channelId, List<String> participants) {
        return channelManager.setProtocolParticipants(channelId, participants);
    }

    // --- Enforcement ---

    @Override
    public ChannelEnforcementConfig channelEnforcement(UUID channelId) {
        Channel ch = channelReader.findById(channelId)
                                  .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        List<String> availableSources = new java.util.ArrayList<>();
        availableSources.add("TYPE_POLICY");
        availableSources.add("CORRELATION_INTEGRITY");
        availableSources.addAll(protocolReader.registeredProtocols());
        return new ChannelEnforcementConfig(
                ch.enforcementMode() != null ? ch.enforcementMode().name() : "ADVISORY",
                ch.enforcementExclusions(),
                availableSources);
    }

    @Override
    public Channel setEnforcementMode(UUID channelId, String mode) {
        EnforcementMode enforcementMode;
        try {
            enforcementMode = EnforcementMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid enforcement mode: " + mode + ". Valid values: ADVISORY, BLOCKING, QUARANTINE");
        }
        return channelManager.setEnforcementMode(channelId, enforcementMode);
    }

    @Override
    public Channel setEnforcementExclusions(UUID channelId, List<String> exclusions) {
        return channelManager.setEnforcementExclusions(channelId, exclusions);
    }

    // --- Routing ---

    @Override
    public ChannelRoutingConfig routingConfig(UUID channelId) {
        Channel ch = channelReader.findById(channelId)
                                  .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        double effectiveThreshold = routingDiagnostics.effectiveThreshold(ch);
        return new ChannelRoutingConfig(
                ch.name(), ch.routingTrustThreshold(), effectiveThreshold, effectiveThreshold);
    }

    @Override
    public RoutingDiagnostic routingCandidates(String capability, UUID channelId) {
        return routingDiagnostics.diagnose(capability, channelId, currentPrincipal.tenancyId());
    }

    @Override
    public Channel setRoutingConfig(UUID channelId, Double trustThreshold) {
        if (trustThreshold != null && (trustThreshold < 0.0 || trustThreshold > 1.0)) {
            throw new IllegalArgumentException("trust_threshold must be between 0.0 and 1.0, got: " + trustThreshold);
        }
        return channelManager.setRoutingTrustThreshold(channelId, trustThreshold);
    }

    // --- Summary ---

    @Override
    public ChannelSummaryResult channelSummary(UUID channelId) {
        Channel ch = channelReader.findById(channelId)
                                  .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        return channelSummaryManager.getSummary(channelId)
                                    .map(s -> toSummaryResult(ch.name(), s))
                                    .orElse(new ChannelSummaryResult(ch.name(), null, java.util.Map.of(), null, null, null, null));
    }

    @Override
    public ChannelSummaryResult updateChannelSummary(UUID channelId, String summary) {
        Channel ch = channelReader.findById(channelId)
                                  .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        ChannelSummary s = channelSummaryManager.setSummary(channelId, summary, currentPrincipal.actorId());
        return toSummaryResult(ch.name(), s);
    }

    @Override
    public ChannelSummaryResult configureChannelSummary(UUID channelId, Integer updateAfterMessages, Integer updateAfterSeconds) {
        Channel ch = channelReader.findById(channelId)
                                  .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        ChannelSummary s = channelSummaryManager.configureSummary(channelId, updateAfterMessages, updateAfterSeconds);
        return toSummaryResult(ch.name(), s);
    }

    @Override
    public ChannelSummaryResult triggerChannelSummaryUpdate(UUID channelId) {
        Channel ch = channelReader.findById(channelId)
                                  .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        return channelSummaryManager.triggerUpdate(channelId)
                                    .map(s -> toSummaryResult(ch.name(), s))
                                    .orElseThrow(() -> new IllegalArgumentException(
                                            "No summary configured for channel '" + ch.name() + "'. Use configure_channel_summary first."));
    }

    // --- Projection ---

    @Override
    public List<String> projections() {
        return projectionReader.registeredNames();
    }

    @Override
    public String projectChannel(UUID channelId, String projectionName, Integer maxMessages, String topic) {
        return projectionReader.project(channelId, projectionName, maxMessages, topic);
    }

    // --- Capacity thresholds ---

    @Override
    public CapacityThresholdConfig redistributionThreshold(UUID channelId) {
        Channel ch = channelReader.findById(channelId)
                                  .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        Double configured = ch.redistributionCapacityThreshold();
        double effective  = configured != null ? configured : 0.85;
        return new CapacityThresholdConfig(ch.name(), configured, effective);
    }

    @Override
    public CapacityThresholdConfig routingCapacityThreshold(UUID channelId) {
        Channel ch = channelReader.findById(channelId)
                                  .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        Double configured = ch.routingCapacityThreshold();
        double effective  = configured != null ? configured : 0.8;
        return new CapacityThresholdConfig(ch.name(), configured, effective);
    }

    @Override
    public Channel setRedistributionThreshold(UUID channelId, Double threshold) {
        if (threshold != null && (threshold < 0.0 || threshold > 1.0)) {
            throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0, got: " + threshold);
        }
        return channelManager.setRedistributionCapacityThreshold(channelId, threshold);
    }

    @Override
    public Channel setRoutingCapacityThreshold(UUID channelId, Double threshold) {
        if (threshold != null && (threshold < 0.0 || threshold > 1.0)) {
            throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0, got: " + threshold);
        }
        return channelManager.setRoutingCapacityThreshold(channelId, threshold);
    }

    // --- Topic queries ---

    @Override
    public List<TopicSummary> topics(UUID channelId) {
        return topicManager.listTopics(channelId);
    }

    // --- Topic mutations ---

    @Override
    public Topic resolveTopic(UUID channelId, String topicName, String actorId) {
        String actor = actorId != null ? actorId : "anonymous";
        return topicManager.resolve(channelId, topicName, actor);
    }

    @Override
    public Topic unresolveTopic(UUID channelId, String topicName) {
        return topicManager.unresolve(channelId, topicName);
    }

    @Override
    public TopicRenameResult renameTopic(UUID channelId, String oldName, String newName, String actorId) {
        String                    actor = actorId != null ? actorId : "anonymous";
        TopicManager.RenameResult r     = topicManager.rename(channelId, oldName, newName, actor);
        return new TopicRenameResult(r.oldName(), r.newName(), r.messagesUpdated());
    }

    @Override
    public TopicMergeResult mergeTopics(UUID channelId, String sourceTopic, String targetTopic, String actorId) {
        String                   actor = actorId != null ? actorId : "anonymous";
        TopicManager.MergeResult r     = topicManager.merge(channelId, sourceTopic, targetTopic, actor);
        return new TopicMergeResult(r.sourceTopic(), r.targetTopic(), r.messagesUpdated());
    }

    @Override
    public TopicMoveResult moveTopic(UUID sourceChannelId, String topicName, UUID targetChannelId, String actorId) {
        TopicManager.MoveResult r = topicManager.move(sourceChannelId, topicName, targetChannelId);
        return new TopicMoveResult(r.topicName(), r.sourceChannelId(), r.targetChannelId(), r.messagesUpdated());
    }

    // --- Membership queries ---

    @Override
    public List<ChannelMembership> members(UUID channelId) {
        return membershipManager.listMembers(channelId);
    }

    @Override
    public List<UnreadCount> unreadCounts(String memberId) {
        return new java.util.ArrayList<>(unreadCountProvider.getUnreadCounts(memberId, currentPrincipal.tenancyId()).values());
    }

    @Override
    public List<MessageDeliveryStatus> messageDeliveryStatus(UUID channelId, Long messageId) {
        Channel ch = channelReader.findById(channelId)
                                  .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        if (!isDeliveryTrackingEnabled(ch)) {
            throw new IllegalStateException("Delivery tracking is not enabled on channel " + ch.name());
        }
        return membershipManager.listMembers(channelId).stream()
                                .map(m -> new MessageDeliveryStatus(
                                        m.memberId(),
                                        m.lastDeliveredMessageId() != null && m.lastDeliveredMessageId() >= messageId,
                                        m.lastDeliveredMessageId()))
                                .toList();
    }

    // --- Membership mutations ---

    @Override
    public ChannelMembership joinChannel(UUID channelId, String memberId, String role) {
        MemberRole memberRole = (role != null && !role.isBlank())
                                ? MemberRole.valueOf(role.toUpperCase())
                                : MemberRole.PARTICIPANT;
        return membershipManager.join(channelId, memberId);
    }

    @Override
    public void leaveChannel(UUID channelId, String memberId) {
        membershipManager.leave(channelId, memberId);
    }

    @Override
    public void markChannelRead(UUID channelId, String memberId, Long messageId) {
        Long effectiveId = messageId;
        if (effectiveId == null) {
            effectiveId = messageStore.findLastMessage(channelId).map(Message::id).orElse(0L);
        }
        membershipManager.markRead(channelId, memberId, effectiveId);
    }

    // --- Space queries ---

    @Override
    public Space space(UUID id) {
        return spaceManager.findById(id).orElse(null);
    }

    @Override
    public List<Space> spaces(UUID parentSpaceId) {
        if (parentSpaceId == null) {return spaceManager.listRoots();}
        return spaceManager.listChildren(parentSpaceId);
    }

    @Override
    public List<Channel> spaceChannels(UUID spaceId) {
        return spaceManager.listChannels(spaceId);
    }

    // --- Space mutations ---

    @Override
    public Space createSpace(SpaceCreateRequest request) {
        return spaceManager.create(request);
    }

    @Override
    public DeleteSpaceResult deleteSpace(UUID spaceId) {
        spaceManager.delete(spaceId);
        return new DeleteSpaceResult(spaceId.toString(), true);
    }

    @Override
    public Space renameSpace(UUID spaceId, String newName) {
        return spaceManager.rename(spaceId, newName);
    }

    @Override
    public Space updateSpaceDescription(UUID spaceId, String description) {
        return spaceManager.updateDescription(spaceId, description);
    }

    @Override
    public Space moveSpace(UUID spaceId, UUID newParentSpaceId) {
        return spaceManager.moveSpace(spaceId, newParentSpaceId);
    }

    @Override
    public Channel moveChannelToSpace(UUID channelId, UUID spaceId) {
        return spaceManager.moveChannelToSpace(channelId, spaceId);
    }

    // --- Gateway queries ---

    @Override
    public List<BackendInfo> backends(UUID channelId) {
        return backendRegistry.listBackends(channelId).stream()
                              .map(r -> new BackendInfo(r.backendId(), r.backendType(),
                                                        r.actorType().name().toLowerCase()))
                              .toList();
    }

    // --- Gateway mutations ---

    @Override
    public void registerBackend(UUID channelId, String backendId, String backendType) {
        backendRegistry.registerBackend(channelId, null, backendType);
    }

    @Override
    public void deregisterBackend(UUID channelId, String backendId) {
        backendRegistry.deregisterBackend(channelId, backendId);
    }

    // --- Force release ---

    @Override
    public ForceReleaseResult forceReleaseChannel(UUID channelId, String reason, String callerInstanceId) {
        Channel ch = channelReader.findById(channelId)
                                  .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        if (ch.semantic() != ChannelSemantic.BARRIER && ch.semantic() != ChannelSemantic.COLLECT) {
            throw new IllegalArgumentException(
                    "force_release_channel only applies to BARRIER and COLLECT channels, not " + ch.semantic().name());
        }
        List<Message> messages = messageStore.scan(
                io.casehub.qhorus.api.store.query.MessageQuery.builder()
                                                              .channelId(ch.id())
                                                              .excludeTypes(List.of(io.casehub.qhorus.api.message.MessageType.EVENT))
                                                              .build());
        return new ForceReleaseResult(ch.name(), ch.semantic().name(), messages.size(), messages);
    }

    // --- Helpers ---

    private static boolean isDeliveryTrackingEnabled(Channel ch) {
        if (ch.trackDelivery() != null) {return ch.trackDelivery();}
        return ch.semantic() == ChannelSemantic.BARRIER || ch.semantic() == ChannelSemantic.COLLECT;
    }

    private static ChannelSummaryResult toSummaryResult(String channelName, ChannelSummary s) {
        return new ChannelSummaryResult(channelName, s.content(), s.annotations(),
                                        s.updatedAt() != null ? s.updatedAt().toString() : null,
                                        s.updatedBy(), s.updateAfterMessages(), s.updateAfterSeconds());
    }
}
