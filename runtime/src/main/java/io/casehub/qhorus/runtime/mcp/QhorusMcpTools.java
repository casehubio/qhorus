package io.casehub.qhorus.runtime.mcp;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.ActorTypeResolver;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelConnectorBinding;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelDetail;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.ChannelSlugValidator;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.channel.TopicManager;
import io.casehub.qhorus.api.data.SharedData;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.Senders;
import io.casehub.qhorus.api.instance.Instance;
import io.casehub.qhorus.api.instance.InstanceInfo;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.Reaction;
import io.casehub.qhorus.api.message.ReactionGroup;
import io.casehub.qhorus.api.message.Topic;
import io.casehub.qhorus.api.message.TopicSummary;
import io.casehub.qhorus.api.spi.InstanceActorIdProvider;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.qhorus.api.store.DataStore;
import io.casehub.qhorus.api.store.InstanceStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.ReactionStore;
import io.casehub.qhorus.api.store.TopicStore;
import io.casehub.qhorus.api.store.WatchdogStore;
import io.casehub.qhorus.api.store.query.ChannelQuery;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.api.watchdog.Watchdog;
import io.casehub.qhorus.runtime.channel.ChannelSummaryService;
import io.casehub.qhorus.runtime.channel.PresenceService;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.qhorus.runtime.instance.InstanceService;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import io.casehub.qhorus.runtime.message.MessageService;
import io.casehub.qhorus.runtime.message.ProjectionRegistry;
import io.casehub.qhorus.runtime.message.ReactionService;
import io.casehub.qhorus.runtime.message.RoutingBridge;
import io.casehub.qhorus.runtime.message.TopicService;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.WrapBusinessError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * All business logic exceptions ({@link IllegalArgumentException} and
 * {@link IllegalStateException}) thrown from any {@code @Tool} method are
 * automatically wrapped in {@link io.quarkiverse.mcp.server.ToolCallException}
 * by the quarkus-mcp-server interceptor, producing an {@code isError: true}
 * tool response with the exception message as text content. This gives Claude
 * readable errors without changing the happy-path return types of the 37
 * structured-return tools. See ADR-0001.
 */
@McpServer("qhorus")
@WrapBusinessError({ IllegalArgumentException.class, IllegalStateException.class })
@ApplicationScoped
public class QhorusMcpTools extends QhorusMcpToolsBase {

    private static final Logger LOG = Logger.getLogger(QhorusMcpTools.class);

    @Inject
    CurrentPrincipal currentPrincipal;

    @Inject
    InstanceService instanceService;

    @Inject
    MessageService messageService;

    @Inject
    io.casehub.qhorus.runtime.data.DataService dataService;

    @Inject
    io.casehub.qhorus.runtime.config.QhorusConfig qhorusConfig;

    @Inject
    MessageLedgerEntryRepository ledgerRepo;

    @Inject
    MessageStore messageStore;

    @Inject
    ChannelStore channelStore;

    @Inject
    DataStore dataStore;

    @Inject
    InstanceStore instanceStore;

    @Inject
    WatchdogStore watchdogStore;

    @Inject
    CommitmentStore commitmentStore;

    @Inject
    io.casehub.qhorus.runtime.ledger.CausalGraphService causalGraphService;

    @Inject
    ChannelGateway channelGateway;

    @Inject
    jakarta.enterprise.inject.Instance<io.casehub.qhorus.api.gateway.ChannelBackend> availableBackends;

    @Inject
    InstanceActorIdProvider instanceActorIdProvider;

    @Inject
    ProjectionRegistry projectionRegistry;

    @Inject
    TopicService topicService;

    @Inject
    ReactionService reactionService;

    @Inject
    PresenceService presenceService;
    @jakarta.inject.Inject
    io.casehub.qhorus.runtime.channel.ChannelMembershipService membershipService;
    @jakarta.inject.Inject
    io.casehub.qhorus.api.store.ChannelMembershipStore         membershipStore;
    @Inject
    ChannelSummaryService                                      channelSummaryService;


    @Inject
    TopicStore topicStore;
    @Inject
    io.casehub.ledger.api.spi.LedgerEntryRepository ledgerEntryRepository;

    @Inject
    io.casehub.qhorus.runtime.ledger.PeerAttestationWriter peerAttestationWriter;

    @Inject
    io.casehub.qhorus.runtime.ledger.ReviewerResolver reviewerResolver;

    @Inject
    jakarta.enterprise.inject.Instance<io.casehub.platform.api.capacity.ActorCapacityView> capacityView;

    @org.eclipse.microprofile.config.inject.ConfigProperty(
            name = "casehub.capacity.redistribution.redistribute-threshold",
            defaultValue = "0.85")
    double globalRedistributeThreshold;


    @Inject
    ReactionStore reactionStore;
    @Inject
    io.casehub.qhorus.runtime.message.protocol.ProtocolRegistry protocolRegistry;
    @Inject
    RoutingBridge                                               routingBridge;


    // ---------------------------------------------------------------------------
    // Instance management tools
    // ---------------------------------------------------------------------------


    @Transactional
    public RegisterResponse register(String instanceId, String description, List<String> capabilities,
                              String claudonySessionId, Boolean readOnly) {
        List<String> caps     = capabilities != null ? capabilities : List.of();
        boolean      ro       = readOnly != null && readOnly;
        Instance     instance = instanceService.register(instanceId, description, caps, claudonySessionId, ro);
        List<ChannelInfo> channels = channelService.listAll().stream()
                                                   .map(ch -> new ChannelInfo(ch.name(), ch.description(), ch.semantic().name()))
                                                   .toList();
        List<InstanceInfo> onlineInstances = buildInstanceInfoList(instanceService.listAll());
        return new RegisterResponse(instance.instanceId(), channels, onlineInstances);
    }

    public List<InstanceInfo> listInstances(String capability) {
        List<Instance> instances = (capability != null && !capability.isBlank())
                                   ? instanceService.findByCapability(capability)
                                   : instanceService.listAll();
        return buildInstanceInfoList(instances);
    }

    @Transactional
    public InstanceInfo getInstance(String instanceId) {
        Instance instance = instanceService.findByInstanceId(instanceId)
                                           .orElseThrow(() -> new IllegalArgumentException("Instance not found: " + instanceId));
        return buildInstanceInfoList(java.util.List.of(instance)).get(0);
    }

    public DeregisterResult deregisterInstance(String instanceId) {
        Instance instance = instanceStore.findByInstanceId(instanceId).orElse(null);
        if (instance == null) {
            return new DeregisterResult(instanceId, false, "Instance not found: " + instanceId);
        }
        instanceStore.delete(instance.id());
        return new DeregisterResult(instanceId, true, "Instance '" + instanceId + "' deregistered");
    }

    /**
     * Convenience overload used by ledger-package tests that need a per-channel registration style.
     * In Qhorus, instance registration is global — the {@code channelName} parameter is accepted
     * for API symmetry but not used for scoping.
     */
    @Transactional
    /** Convenience overload — no role or extra. Backward compatibility for tests. */
    public RegisterResponse registerInstance(String channelName, String instanceId,
            String description, List<String> capabilities, String claudonySessionId) {
        return registerInstance(channelName, instanceId, description, capabilities, claudonySessionId, null, null);
    }

    public RegisterResponse registerInstance(
            String channelName,
            String instanceId,
            String description,
            List<String> capabilities,
            String claudonySessionId,
            String role,
            String extra) {
        List<String> caps = capabilities != null ? capabilities : List.of();
        Instance instance = instanceService.register(instanceId,
                description != null ? description : instanceId, caps, claudonySessionId);

        List<ChannelInfo> channels = channelService.listAll().stream()
                                                   .map(ch -> new ChannelInfo(ch.name(), ch.description(), ch.semantic().name()))
                                                   .toList();

        List<InstanceInfo> onlineInstances = buildInstanceInfoList(instanceService.listAll());
        return new RegisterResponse(instance.instanceId(), channels, onlineInstances);
    }

    // ---------------------------------------------------------------------------
    // Channel management tools
    // ---------------------------------------------------------------------------

    @Tool(name = "create_channel", description = "Create a new communication channel for agents to exchange messages. " +
                                                 "Returns channel details including the generated UUID and configured properties.")
    public ChannelDetail createChannel(
            @ToolArg(name = "name", description = "Unique channel name. Each /-delimited segment must match " +
                                                  "[a-z][a-z0-9]*(-[a-z0-9]+)* — lowercase letters and digits, hyphens only between " +
                                                  "alphanumeric groups. No leading, trailing, or consecutive hyphens. Max 80 chars per " +
                                                  "segment, 200 chars total. UUID-shaped names are rejected. " +
                                                  "Examples: \"billing-output\", \"case-abc/work\".") String name,
            @ToolArg(name = "description", description = "Channel purpose description") String description,
            @ToolArg(name = "semantic", description = "Channel semantic: APPEND (default), COLLECT, BARRIER, EPHEMERAL, LAST_WRITE", required = false) String semantic,
            @ToolArg(name = "barrier_contributors", description = "Comma-separated contributor names (BARRIER channels only)", required = false) String barrierContributors,
            @ToolArg(name = "allowed_writers", description = "Comma-separated allowed writers: bare instance IDs and/or capability:tag / role:name patterns. Null = open to all.", required = false) String allowedWriters,
            @ToolArg(name = "admin_instances", description = "Comma-separated instance IDs permitted to manage this channel (pause/resume/force_release/clear). Null = open to any caller.", required = false) String adminInstances,
            @ToolArg(name = "rate_limit_per_channel", description = "Max messages per minute across all senders. Null = unlimited.", required = false) Integer rateLimitPerChannel,
            @ToolArg(name = "rate_limit_per_instance", description = "Max messages per minute from a single sender. Null = unlimited.", required = false) Integer rateLimitPerInstance,
            @ToolArg(name = "allowed_types", description = "Comma-separated MessageType names permitted on this channel. Null = all types permitted.", required = false) String allowedTypes,
            @ToolArg(name = "denied_types", description = "Comma-separated MessageType names explicitly denied on this channel. Denial wins if a type appears in both.", required = false) String deniedTypes,
            @ToolArg(name = "space_id", description = "Space UUID to place this channel in. Null = top-level channel.", required = false) String spaceId,
            @ToolArg(name = "reviewer_ids", description = "Comma-separated reviewer instance IDs for automatic peer review after DONE. Null = no auto-review.", required = false) String reviewerIds,
            @ToolArg(name = "protocols", description = "Comma-separated protocol names to enforce on this channel (e.g. ROUND_ROBIN,CONTRIBUTION_REQUIRED). Null = no protocols.", required = false) String protocols,
            @ToolArg(name = "protocol_participants", description = "Comma-separated ordered participant IDs for protocol enforcement. Required for ROUND_ROBIN.", required = false) String protocolParticipants,
            @ToolArg(name = "inbound_connector_id", description = "Inbound connector type identifier. All four connector fields must be set together or left null.", required = false) String inboundConnectorId,
            @ToolArg(name = "external_key", description = "Connector-specific lookup key.", required = false) String externalKey,
            @ToolArg(name = "outbound_connector_id", description = "Outbound connector type identifier.", required = false) String outboundConnectorId,
            @ToolArg(name = "outbound_destination", description = "Outbound destination address.", required = false) String outboundDestination,
            @ToolArg(name = "track_delivery", description = "Enable per-participant delivery tracking. Null = semantic default (on for BARRIER/COLLECT, off for others). true = explicit opt-in, false = explicit opt-out.", required = false) Boolean trackDelivery) {
        ChannelSemantic sem;
        if (semantic == null || semantic.isBlank()) {
            sem = ChannelSemantic.APPEND;
        } else {
            try {
                sem = ChannelSemantic.valueOf(semantic.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid semantic '" + semantic + "'. Valid values: APPEND, COLLECT, BARRIER, EPHEMERAL, LAST_WRITE");
            }
        }
        Channel ch = channelService.create(ChannelCreateRequest.builder(name)
                                                               .description(description)
                                                               .semantic(sem)
                                                               .barrierContributors(splitCsv(barrierContributors))
                                                               .allowedWriters(splitCsv(allowedWriters))
                                                               .adminInstances(splitCsv(adminInstances))
                                                               .rateLimitPerChannel(rateLimitPerChannel)
                                                               .rateLimitPerInstance(rateLimitPerInstance)
                                                               .allowedTypes(MessageType.parseTypes(allowedTypes))
                                                               .deniedTypes(MessageType.parseTypes(deniedTypes))
                                                               .spaceId(spaceId != null ? resolveSpace(spaceId).id() : null)
                                                               .reviewerInstances(splitCsv(reviewerIds))
                                                               .protocols(splitCsv(protocols))
                                                               .protocolParticipants(splitCsv(protocolParticipants))
                                                               .inboundConnectorId(inboundConnectorId)
                                                               .externalKey(externalKey)
                                                               .outboundConnectorId(outboundConnectorId)
                                                               .outboundDestination(outboundDestination)
                                                               .trackDelivery(trackDelivery)
                                                               .build());
        return toChannelDetail(ch, 0L);
    }




    @Transactional
    public Map<String, Object> attest(
            @ToolArg(name = "entry_id", description = "UUID of the COMMAND/HANDOFF ledger entry") String entryId,
            @ToolArg(name = "verdict", description = "ENDORSED or CHALLENGED") String verdict,
            @ToolArg(name = "evidence", description = "Free-text evidence for the attestation", required = false) String evidence) {
        UUID id = UUID.fromString(entryId);
        io.casehub.ledger.api.model.AttestationVerdict v =
                io.casehub.ledger.api.model.AttestationVerdict.valueOf(verdict.toUpperCase());
        String tenancyId   = currentPrincipal.tenancyId();
        String attestorId  = currentPrincipal.actorId();
        var    attestation = peerAttestationWriter.write(id, v, evidence, attestorId, tenancyId);
        return Map.of("attestation_id", attestation.id,
                      "entry_id", id, "verdict", v.name(), "attestor_id", attestorId);
    }


    public List<Map<String, Object>> listAttestations(
            @ToolArg(name = "entry_id", description = "UUID of the ledger entry") String entryId) {
        UUID   id        = UUID.fromString(entryId);
        String tenancyId = currentPrincipal.tenancyId();
        return ledgerEntryRepository.findAttestationsByEntryId(id, tenancyId).stream()
                                    .map(a -> {
                                        var map = new java.util.LinkedHashMap<String, Object>();
                                        map.put("attestation_id", a.id);
                                        map.put("verdict", a.verdict.name());
                                        map.put("attestor_id", a.attestorId);
                                        map.put("attestor_role", a.attestorRole != null ? a.attestorRole : "policy");
                                        map.put("evidence", a.evidence != null ? a.evidence : "");
                                        map.put("confidence", a.confidence);
                                        map.put("occurred_at", a.occurredAt != null ? a.occurredAt.toString() : "");
                                        return (Map<String, Object>) map;
                                    })
                                    .toList();
    }

    @Transactional
    public Map<String, Object> requestPeerReview(
            @ToolArg(name = "entry_id", description = "UUID of the COMMAND/HANDOFF ledger entry") String entryId,
            @ToolArg(name = "reviewer_ids", description = "Comma-separated reviewer instance IDs. Resolved automatically if omitted.", required = false) String reviewerIds,
            @ToolArg(name = "channel", description = "Channel for the review QUERYs. Defaults to the entry's channel.", required = false) String channel) {
        UUID   id        = UUID.fromString(entryId);
        String tenancyId = currentPrincipal.tenancyId();
        var entry = (io.casehub.qhorus.runtime.ledger.MessageLedgerEntry) ledgerEntryRepository
                                                                                  .findEntryById(id, tenancyId)
                                                                                  .orElseThrow(() -> new IllegalArgumentException("Ledger entry not found: " + id));
        if (!"COMMAND".equals(entry.messageType) && !"HANDOFF".equals(entry.messageType)) {
            throw new IllegalArgumentException("Entry must be COMMAND or HANDOFF, not " + entry.messageType);
        }

        UUID         channelId = channel != null ? resolveChannel(channel).id() : entry.channelId;
        List<String> reviewers = reviewerResolver.resolve(channelId, splitCsv(reviewerIds), id, tenancyId);
        if (reviewers.isEmpty()) {
            return Map.of("reviewers_sent", 0, "advisory", "No reviewers resolved — configure channel reviewers or register instances with peer-reviewer capability.");
        }

        String completionContent = null;
        if (entry.correlationId != null) {
            var terminalEntry = ledgerRepo.findLatestByCorrelationId(entry.channelId, entry.correlationId, tenancyId);
            if (terminalEntry.isPresent()) {
                completionContent = terminalEntry.get().content;
            }
        }

        var sentReviews = new java.util.ArrayList<Map<String, String>>();
        for (String reviewerId : reviewers) {
            try {
                var peerReview = mapper.createObjectNode();
                peerReview.put("ledger_entry_id", id.toString());
                peerReview.put("original_command", entry.content);
                peerReview.put("completion_content", completionContent);
                var content = mapper.createObjectNode();
                content.set("peer_review", peerReview);

                String corrId = UUID.randomUUID().toString();
                messageService.dispatch(MessageDispatch.builder()
                                                       .channelId(channelId)
                                                       .sender(currentPrincipal.actorId())
                                                       .type(MessageType.QUERY)
                                                       .content(mapper.writeValueAsString(content))
                                                       .correlationId(corrId)
                                                       .target(reviewerId)
                                                       .actorType(ActorType.SYSTEM)
                                                       .tenancyId(tenancyId)
                                                       .build());
                sentReviews.add(Map.of("reviewer_id", reviewerId, "correlation_id", corrId));
            } catch (Exception e) {
                LOG.warnf(e, "Failed to send peer review QUERY to %s for entry %s", reviewerId, id);
            }
        }
        return Map.of("reviewers_sent", sentReviews.size(), "reviews", sentReviews);
    }


    // ---------------------------------------------------------------------------
    // Human-in-the-loop — channel flow control
    // ---------------------------------------------------------------------------

    /** Convenience overload — no caller identity (open governance assumed). */
    ChannelDetail pauseChannel(String channel) {
        return pauseChannel(channel, null);
    }

    @Tool(name = "pause_channel", description = "Pause a channel — blocks send_message and returns empty on check_messages. "
            + "Idempotent. Use to stop agent work flowing through a channel for human review.")
    @Transactional
    public ChannelDetail pauseChannel(
            @ToolArg(name = "channel", description = "Channel name or UUID") String channel,
            @ToolArg(name = "caller_instance_id", description = "Instance ID of the caller. Required when the channel has an admin_instances list.", required = false) String callerInstanceId) {
        Channel ch = resolveChannel(channel);
        checkAdminAccess(ch, callerInstanceId, "pause_channel");
        ch = channelService.pause(ch.id());
        return toChannelDetail(ch, messageStore.countByChannel(ch.id()));
    }

    /** Convenience overload — no caller identity (open governance assumed). */
    ChannelDetail resumeChannel(String channel) {
        return resumeChannel(channel, null);
    }

    @Tool(name = "resume_channel", description = "Resume a paused channel — re-enables send_message and check_messages. "
            + "Idempotent.")
    @Transactional
    public ChannelDetail resumeChannel(
            @ToolArg(name = "channel", description = "Channel name or UUID") String channel,
            @ToolArg(name = "caller_instance_id", description = "Instance ID of the caller. Required when the channel has an admin_instances list.", required = false) String callerInstanceId) {
        Channel ch = resolveChannel(channel);
        checkAdminAccess(ch, callerInstanceId, "resume_channel");
        ch = channelService.resume(ch.id());
        return toChannelDetail(ch, messageStore.countByChannel(ch.id()));
    }

    /** Convenience overload — no caller identity (open governance assumed). */
    DeleteChannelResult deleteChannel(String channel, Boolean force) {
        return deleteChannel(channel, force, null);
    }

    @Tool(name = "delete_channel", description = "Delete a named channel. "
            + "Rejects with an error if the channel has messages unless force=true. "
            + "When force=true, all messages in the channel are deleted before the channel is removed. "
            + "Subject to admin_instances check if the channel has an admin list.")
    @Transactional
    public DeleteChannelResult deleteChannel(
            @ToolArg(name = "channel", description = "Channel name or UUID") String channel,
            @ToolArg(name = "force", description = "When true, deletes all messages in the channel then "
                    + "deletes the channel. When false (default), rejects if messages exist.", required = false) Boolean force,
            @ToolArg(name = "caller_instance_id", description = "Instance ID of the caller. Required when the channel has an admin_instances list.", required = false) String callerInstanceId) {
        Channel ch = resolveChannel(channel);
        checkAdminAccess(ch, callerInstanceId, "delete_channel");
        membershipStore.deleteAll(ch.id());
        reactionStore.deleteByChannel(ch.id());
        commitmentStore.deleteAll(ch.id());
        topicStore.deleteAll(ch.id());
        long deleted = channelService.delete(ch.id(), Boolean.TRUE.equals(force));
        channelGateway.closeChannel(ch.id(), new ChannelRef(ch.id(), ch.name()));
        return new DeleteChannelResult(ch.name(), deleted, "deleted");
    }




    // ---------------------------------------------------------------------------
    // Messaging tools
    // ---------------------------------------------------------------------------


    @Tool(name = "send_message", description = "Post a typed message to a channel. "
            + "For QUERY, COMMAND, and PROPOSE types, correlation_id is auto-generated if not supplied.")
    @Transactional
    public DispatchResult sendMessage(
            @ToolArg(name = "channel", description = "Channel name or UUID") String channel,
            @ToolArg(name = "sender", description = "Sender identifier") String sender,
            @ToolArg(name = "type", description = "The message type. Choose: QUERY (asking for information, no side effects), COMMAND (asking for action to be taken, side effects expected), RESPONSE (answering a QUERY, carries correlationId), STATUS (reporting progress on a COMMAND, extends deadline), DECLINE (refusing a QUERY or COMMAND, content must explain why), HANDOFF (transferring obligation to another agent, target required), DONE (signalling successful completion of a COMMAND), FAILURE (signalling unsuccessful termination, content must explain why), PROPOSE (offering conditional commitment — sender binds to action contingent on receiver's acceptance; RESPONSE does not auto-fulfill, only DONE accepts), EVENT (telemetry only, not delivered to agents)") String type,
            @ToolArg(name = "content", description = "Message content") String content,
            @ToolArg(name = "payload", description = "Structured data payload (JSON string). Carried alongside content for machine-readable data (tool results, parameters). Not analyzed by governance.", required = false) String payload,
            @ToolArg(name = "correlation_id", description = "Correlation ID (auto-generated for QUERY, COMMAND, and PROPOSE if omitted)", required = false) String correlationId,
            @ToolArg(name = "in_reply_to", description = "ID of the message being replied to", required = false) Long inReplyTo,
            @ToolArg(name = "artefact_refs", description = "UUIDs of shared artefacts to attach. Auto-claims each artefact for the sender; auto-released on commitment resolution (RESPONSE/DONE/DECLINE/FAILURE).", required = false) List<String> artefactRefs,
            @ToolArg(name = "target", description = "Addressing target: instance:<id>, capability:<tag>, or role:<name>. Null/omitted = broadcast to all.", required = false) String target,
            @ToolArg(name = "deadline", description = "Optional deadline as ISO-8601 duration (e.g. PT30M for 30 minutes). Only meaningful for QUERY, COMMAND, and PROPOSE. Defaults to channel config when not provided.", required = false) String deadline,
            @ToolArg(name = "subject_id", description = "Optional UUID of the domain aggregate this message concerns (for ledger indexing).", required = false) String subjectId,
            @ToolArg(name = "caused_by_entry_id", description = "Optional UUID of the ledger entry that triggered this dispatch (for causal chain tracing).", required = false) String causedByEntryId,
            @ToolArg(name = "topic", description = "Topic name for this message. Groups messages into named sub-conversations within the channel. Defaults to 'general' if omitted.", required = false) String topic) {
        Channel ch = resolveChannel(channel);

        // Read-only instance check — read_only instances cannot send any messages (MCP-specific)
        instanceService.findByInstanceId(sender).ifPresent(inst -> {
            if (inst.readOnly()) {
                throw new IllegalStateException(
                        "Instance '" + sender + "' is read-only and cannot send messages. "
                                + "Use check_messages with include_events=true to receive EVENT messages.");
            }
        });

        MessageType msgType = MessageType.valueOf(type.toUpperCase());

        if (msgType.requiresContent() && (content == null || content.isBlank())) {
            throw new IllegalArgumentException(msgType.name() + " requires non-empty content explaining the reason.");
        }
        if (msgType.requiresTarget() && (target == null || target.isBlank())) {
            throw new IllegalArgumentException(
                    "HANDOFF requires a non-null target (instance:id, capability:tag, or role:name).");
        }

        String corrId = correlationId;
        if (corrId == null && msgType.requiresCorrelationId()) {
            corrId = java.util.UUID.randomUUID().toString();
        }

        // Parse optional UUID params — fail early if malformed
        final UUID subjectIdUuid = parseOptionalUuid("subject_id", subjectId);
        final UUID causedByEntryIdUuid = parseOptionalUuid("caused_by_entry_id", causedByEntryId);

        // Build ArtefactRef list — selective validation: UUID refs validated against SharedData, non-UUID bypass
        java.util.List<io.casehub.qhorus.api.message.ArtefactRef> refsList = null;
        if (artefactRefs != null && !artefactRefs.isEmpty()) {
            java.util.List<io.casehub.qhorus.api.message.ArtefactRef> built = new java.util.ArrayList<>(artefactRefs.size());
            java.util.List<java.util.UUID> uuidRefs = new java.util.ArrayList<>();
            for (String ref : artefactRefs) {
                try {
                    java.util.UUID parsed = java.util.UUID.fromString(ref);
                    uuidRefs.add(parsed);
                    built.add(new io.casehub.qhorus.api.message.ArtefactRef(ref, io.casehub.qhorus.api.message.ArtefactType.DOCUMENT, null, null));
                } catch (IllegalArgumentException e) {
                    built.add(new io.casehub.qhorus.api.message.ArtefactRef(ref, io.casehub.qhorus.api.message.ArtefactType.EXTERNAL, null, null));
                }
            }
            if (!uuidRefs.isEmpty()) {
                java.util.List<java.util.UUID> found = dataStore.findByIds(uuidRefs).stream().map(SharedData::id).toList();
                java.util.List<java.util.UUID> unknown = uuidRefs.stream().filter(u -> !found.contains(u)).toList();
                if (!unknown.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Unknown artefact ref(s): " + unknown.stream().map(java.util.UUID::toString).collect(java.util.stream.Collectors.joining(", ")));
                }
                instanceService.findByInstanceId(sender).ifPresent(inst -> {
                    for (java.util.UUID uuid : uuidRefs) {
                        dataService.claim(uuid, inst.id());
                    }
                });
            }
            refsList = java.util.List.copyOf(built);
        }

        // Validate and normalise target — null/blank → no addressing (broadcast)
        String normalisedTarget = (target == null || target.isBlank()) ? null : target.strip();
        if (normalisedTarget != null) {
            if (!normalisedTarget.startsWith("instance:") &&
                    !normalisedTarget.startsWith("capability:") &&
                    !normalisedTarget.startsWith("role:")) {
                throw new IllegalArgumentException(
                        "Invalid target format: '" + normalisedTarget
                                + "'. Must be instance:<id>, capability:<tag>, or role:<name>.");
            }
            String valuePart = normalisedTarget.substring(normalisedTarget.indexOf(':') + 1);
            if (valuePart.isBlank()) {
                throw new IllegalArgumentException(
                        "Invalid target format: '" + normalisedTarget
                                + "'. Value after prefix cannot be empty.");
            }
        }

        ActorType resolvedActorType =
                ActorTypeResolver.resolve(instanceActorIdProvider.resolve(sender));

        DispatchResult dispatchResult = messageService.dispatch(
                MessageDispatch.builder()
                        .channelId(ch.id())
                        .sender(sender)
                        .type(msgType)
                        .content(content)
                        .payload(payload)
                        .correlationId(corrId)
                        .inReplyTo(inReplyTo)
                        .artefactRefs(refsList)
                        .target(normalisedTarget)
                        .subjectId(subjectIdUuid)
                        .causedByEntryId(causedByEntryIdUuid)
                        .actorType(resolvedActorType)
                        .topic(topic)
                        .build());

        // Fetch the persisted entity to write deadline as a dirty-entity update in the same transaction.
        if (deadline != null && !deadline.isBlank() && msgType.requiresCorrelationId()) {
            Message msg = messageService.findById(dispatchResult.messageId()).orElseThrow();
            messageStore.put(msg.toBuilder()
                    .deadline(java.time.Instant.now().plus(java.time.Duration.parse(deadline))).build());
        }

        // Auto-release artefact claims when a commitment resolves (DONE/DECLINE/FAILURE, or RESPONSE on non-PROPOSE).
        // Find the original QUERY/COMMAND/PROPOSE message by correlationId and release the requester's claims.
        // HANDOFF delegates obligation — claims stay until the delegate resolves.
        // RESPONSE on PROPOSE is non-fulfilling — claims must NOT be released.
        boolean isCommitmentResolving = msgType == MessageType.DONE
                || msgType == MessageType.DECLINE || msgType == MessageType.FAILURE;
        if (!isCommitmentResolving && msgType == MessageType.RESPONSE && dispatchResult.correlationId() != null) {
            var commitment = commitmentStore.findByCorrelationId(dispatchResult.correlationId());
            isCommitmentResolving = commitment.isEmpty()
                    || commitment.get().messageType() != MessageType.PROPOSE;
        }
        if (dispatchResult.correlationId() != null && isCommitmentResolving) {
            try {
                messageService.findByCorrelationId(dispatchResult.correlationId()).ifPresent(original -> {
                    if (original.artefactRefs() != null && !original.artefactRefs().isEmpty()) {
                        instanceService.findByInstanceId(original.sender()).ifPresent(inst -> {
                            for (io.casehub.qhorus.api.message.ArtefactRef ref : original.artefactRefs()) {
                                try { dataService.release(UUID.fromString(ref.uri()), inst.id()); }
                                catch (IllegalArgumentException ignored) {}
                            }
                        });
                    }
                });
            } catch (Exception e) {
                LOG.warnf("Auto-release artefact claims failed for correlationId '%s': %s",
                        dispatchResult.correlationId(), e.getMessage());
            }
        }

        return dispatchResult;
    }


    /** Backward-compat overload — no reader_instance_id filter. */
    List<MessageSummary> getReplies(Long messageId) {
        return getReplies(messageId, null, null, null);
    }

    List<MessageSummary> getReplies(Long messageId, String readerInstanceId) {
        return getReplies(messageId, readerInstanceId, null, null);
    }

    @Tool(name = "get_replies", description = "Retrieve direct replies to a specific message.")
    @Transactional
    public List<MessageSummary> getReplies(
            @ToolArg(name = "message_id", description = "ID of the parent message") Long messageId,
            @ToolArg(name = "reader_instance_id", description = "Calling agent's instance ID for target filtering", required = false) String readerInstanceId,
            @ToolArg(name = "after_id", description = "Return replies with id > after_id (cursor pagination)", required = false) Long afterId,
            @ToolArg(name = "limit", description = "Maximum replies to return (default 20, max 100)", required = false) Integer limit) {
        final int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 100) : 20;
        final String query = afterId != null
                ? "inReplyTo = ?1 AND id > ?2 ORDER BY id ASC"
                : "inReplyTo = ?1 ORDER BY id ASC";
        MessageQuery.Builder mqb = MessageQuery.builder().inReplyTo(messageId).limit(effectiveLimit);
        if (afterId != null) mqb.afterId(afterId);
        final List<Message> messages = messageStore.scan(mqb.build());
        return messages.stream()
                .filter(m -> isVisibleToReader(m, readerInstanceId,
                        () -> instanceService.findCapabilityTagsForInstance(readerInstanceId)))
                .map(this::toMessageSummary)
                .toList();
    }

    /** Backward-compat overload — no reader_instance_id filter. */
    List<MessageSummary> searchMessages(String query, String channel, Integer limit) {
        return searchMessages(query, channel, limit, null);
    }

    @Tool(name = "search_messages", description = "Full-text keyword search across messages. Excludes EVENT type.")
    public List<MessageSummary> searchMessages(
            @ToolArg(name = "query", description = "Keyword to search for (case-insensitive)") String query,
            @ToolArg(name = "channel", description = "Channel name or UUID", required = false) String channel,
            @ToolArg(name = "limit", description = "Maximum results (default 20)", required = false) Integer limit,
            @ToolArg(name = "reader_instance_id", description = "Calling agent's instance ID for target filtering (optional)", required = false) String readerInstanceId) {
        String pattern = "%" + query.toLowerCase() + "%";
        int pageSize = limit != null ? limit : 20;

        Channel ch = null;
        if (channel != null && !channel.isBlank()) {
            ch = resolveChannel(channel);
        }
        UUID channelId = ch != null ? ch.id() : null;

        MessageQuery.Builder sqb = MessageQuery.builder()
                .contentPattern(query)
                .excludeTypes(List.of(MessageType.EVENT))
                .limit(pageSize);
        if (channelId != null) sqb.channelId(channelId);
        List<Message> results = messageStore.scan(sqb.build());

        return results.stream()
                .filter(m -> isVisibleToReader(m, readerInstanceId,
                        () -> instanceService.findCapabilityTagsForInstance(readerInstanceId)))
                .map(this::toMessageSummary).toList();
    }

    @Tool(name = "get_message", description = "Look up a message by its numeric ID. "
            + "Returns the message summary including content, type, sender, and metadata. "
            + "Throws an error if the message is not found.")
    public MessageSummary getMessage(
            @ToolArg(name = "message_id", description = "Numeric message ID") Long messageId) {
        Message message = messageService.findById(messageId)
                                              .orElseThrow(() -> new IllegalArgumentException(
                        "Message not found: " + messageId));
        return toMessageSummary(message);
    }

    // ---------------------------------------------------------------------------
    // Correlation / wait_for_reply
    // ---------------------------------------------------------------------------

    @Tool(name = "wait_for_reply", description = "Block until a RESPONSE message with the given correlation_id "
            + "arrives on the channel, or until timeout_seconds seconds elapse. "
            + "Returns immediately if a matching response already exists.")
    public WaitResult waitForReply(
            @ToolArg(name = "channel", description = "Channel name or UUID") String channel,
            @ToolArg(name = "correlation_id", description = "UUID matching the correlation_id on the expected RESPONSE") String correlationId,
            @ToolArg(name = "timeout_seconds", description = "Seconds to wait before timing out (default 90)", required = false) Integer timeoutS,
            @ToolArg(name = "instance_id", description = "Waiting agent's instance ID for tracking (optional)", required = false) String instanceId) {
        Channel ch = resolveChannel(channel);

        int timeout = timeoutS != null ? timeoutS : 90;
        java.time.Instant expiresAt = java.time.Instant.now().plusSeconds(timeout);

        // Poll loop — each check is its own short transaction so we don't hold a connection.
        // Commitment was already created by CommitmentService.open() when QUERY/COMMAND was sent.
        long pollMs = 100;
        while (java.time.Instant.now().isBefore(expiresAt)) {
            Optional<Commitment> opt = commitmentStore.findByCorrelationId(correlationId);
            if (opt.isEmpty()) {
                // Commitment deleted by cancel_wait — return cancelled
                return new WaitResult(false, false, correlationId, null,
                        "Wait cancelled for correlation_id=" + correlationId);
            }
            Commitment commitment = opt.get();
            if (commitment.state() == CommitmentState.FULFILLED
                    || commitment.state() == CommitmentState.OPEN
                    || commitment.state() == CommitmentState.ACKNOWLEDGED
                    || commitment.state() == CommitmentState.DELEGATED) {
                // Check for RESPONSE or DONE message — covers both the normal FULFILLED path and
                // the race-condition path where a RESPONSE arrived before the QUERY created the Commitment
                // (e.g. approval gate with pre-seeded responses, or distributed message races).
                Message response = messageService.findResponseByCorrelationId(ch.id(), correlationId)
                                                       .orElse(null);
                if (response != null) {
                    return new WaitResult(true, false, correlationId, toMessageSummary(response),
                            "Response received for correlation_id=" + correlationId);
                }
                Message done = messageService.findDoneByCorrelationId(ch.id(), correlationId)
                                                   .orElse(null);
                if (done != null) {
                    return new WaitResult(true, false, correlationId, toMessageSummary(done),
                            "Done received for correlation_id=" + correlationId);
                }
            }
            if (commitment.state() == CommitmentState.DECLINED) {
                return new WaitResult(false, false, correlationId, null,
                        "Request was DECLINED for correlation_id=" + correlationId);
            }
            if (commitment.state() == CommitmentState.FAILED) {
                return new WaitResult(false, false, correlationId, null,
                        "Request FAILED for correlation_id=" + correlationId);
            }
            if (commitment.state() == CommitmentState.EXPIRED) {
                return new WaitResult(false, true, correlationId, null,
                        "Commitment EXPIRED for correlation_id=" + correlationId);
            }
            // OPEN, ACKNOWLEDGED, DELEGATED with no message yet — keep waiting
            try {
                Thread.sleep(pollMs);
                pollMs = Math.min(pollMs * 2, 500); // backoff up to 500ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return new WaitResult(false, true, correlationId, null,
                "Timed out after " + timeout + "s waiting for response to correlation_id=" + correlationId);
    }

    // ---------------------------------------------------------------------------
    // Human-in-the-loop — approval gate
    // ---------------------------------------------------------------------------

    @Tool(name = "request_approval", description = "Send an approval request to a channel and block until a human responds. "
            + "Returns the human's response or a timeout result. "
            + "Pair with list_pending_commitments (for human to discover) and respond_to_approval (for human to answer).")
    public WaitResult requestApproval(
            @ToolArg(name = "channel", description = "Channel name or UUID") String channel,
            @ToolArg(name = "content", description = "The approval request content shown to the human") String content,
            @ToolArg(name = "timeout_seconds", description = "Seconds to wait for human response (default 300)", required = false) Integer timeoutS) {
        Channel ch            = resolveChannel(channel);
        String        correlationId = UUID.randomUUID().toString();
        return requestApprovalWithCorrelationId(ch.name(), content, correlationId, timeoutS);
    }

    /**
     * Testability overload — accepts a pre-supplied correlationId so tests can pre-seed the response.
     * Not exposed as an MCP tool.
     */
    public WaitResult requestApprovalWithCorrelationId(String channelName, String content, String correlationId,
                                                       Integer timeoutS) {
        int timeout = timeoutS != null ? timeoutS : 300;
        sendMessage(channelName, "agent", "query", content, null, correlationId, null, (List<String>) null, null, null, null, null, null);
        return waitForReply(channelName, correlationId, timeout, null);
    }

    @Tool(name = "respond_to_approval", description = "Human-callable: send a response to a pending approval request. "
            + "Use correlation_id from list_pending_commitments to identify which request to answer.")
    @Transactional
    public DispatchResult respondToApproval(
            @ToolArg(name = "correlation_id", description = "Correlation ID of the approval request (from list_pending_commitments)") String correlationId,
            @ToolArg(name = "response_text", description = "The approval decision or message to send back") String responseText,
            @ToolArg(name = "channel", description = "Channel name or UUID") String channel) {
        Channel ch = resolveChannel(channel);
        // Look up the original request message to supply inReplyTo (required by RESPONSE type).
        // Use canonical MessageDispatch constructor to bypass builder validation when no prior message exists
        // (e.g., when commitment was opened directly without a corresponding channel message).
        Long inReplyTo = messageService.findByCorrelationId(correlationId)
                .map(m -> m.id())
                .orElse(null);
        // Use canonical constructor to bypass builder validation when inReplyTo is null —
        // respondToApproval is a human tool and must not fail even on unusual commitment states.
        io.casehub.qhorus.api.message.MessageDispatch dispatch = new io.casehub.qhorus.api.message.MessageDispatch(
                ch.id(), Senders.HUMAN, io.casehub.qhorus.api.message.MessageType.RESPONSE,
                responseText, null, correlationId, inReplyTo, null, null, null, null,
                io.casehub.platform.api.identity.ActorType.HUMAN, null, null, null, null);
        return messageService.dispatch(dispatch);
    }

    // ---------------------------------------------------------------------------
    // Human-in-the-loop — wait management
    // ---------------------------------------------------------------------------

    @Tool(name = "cancel_wait", description = "Cancel a pending wait_for_reply by its correlation_id. "
            + "The waiting agent receives status='cancelled' instead of timing out. "
            + "Use list_pending_commitments to discover what is blocked.")
    @Transactional
    public CancelWaitResult cancelWait(
            @ToolArg(name = "correlation_id", description = "Correlation ID of the pending wait to cancel") String correlationId) {
        Optional<Commitment> opt = commitmentStore.findByCorrelationId(correlationId);
        if (opt.isPresent()) {
            commitmentStore.deleteById(opt.get().id());
            return new CancelWaitResult(correlationId, true,
                    "Cancelled pending wait for correlation_id=" + correlationId);
        } else {
            return new CancelWaitResult(correlationId, false,
                    "No pending wait found for correlation_id=" + correlationId);
        }
    }

    @Transactional
    public List<CommitmentDetail> listPendingCommitments() {
        return commitmentStore.findAllOpen().stream()
                .map(CommitmentDetail::from)
                .toList();
    }

    // ---------------------------------------------------------------------------
    // Commitment observability
    // ---------------------------------------------------------------------------

    @Transactional
    public List<CommitmentDetail> listMyCommitments(
            @ToolArg(name = "channel", description = "Channel name or UUID") String channel,
            @ToolArg(name = "sender", description = "Your agent identity") String sender,
            @ToolArg(name = "role", description = "Filter: 'obligor', 'requester', or 'both' (default: both)", required = false) String role) {
        Channel ch = resolveChannel(channel);
        String        r  = role == null ? "both" : role.toLowerCase();
        List<Commitment> results = switch (r) {
            case "obligor" -> commitmentStore.findOpenByObligor(sender, ch.id());
            case "requester" -> commitmentStore.findOpenByRequester(sender, ch.id());
            default -> {
                var list = new java.util.ArrayList<>(
                        commitmentStore.findOpenByObligor(sender, ch.id()));
                list.addAll(commitmentStore.findOpenByRequester(sender, ch.id()));
                list.sort(java.util.Comparator.comparing(c -> c.createdAt()));
                yield list;
            }
        };
        return results.stream().map(CommitmentDetail::from).toList();
    }

    @Transactional
    public CommitmentDetail getCommitment(
            @ToolArg(name = "correlation_id", description = "The correlation_id of the QUERY or COMMAND") String correlationId) {
        return commitmentStore.findByCorrelationId(correlationId)
                .map(CommitmentDetail::from)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No commitment found for correlation_id=" + correlationId));
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private List<InstanceInfo> buildInstanceInfoList(List<Instance> instances) {
        if (instances.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = instances.stream().map(i -> i.id()).toList();
        Map<UUID, List<String>> capsByInstanceId = ids.stream()
                .collect(Collectors.toMap(id -> id, id -> instanceStore.findCapabilities(id)));

        return instances.stream()
                .map(i -> new InstanceInfo(
                        i.instanceId(),
                        i.description(),
                        i.status(),
                        capsByInstanceId.getOrDefault(i.id(), List.of()),
                        i.lastSeen().toString(),
                        i.readOnly()))
                .toList();
    }

    // ---------------------------------------------------------------------------
    // Shared data tools
    // ---------------------------------------------------------------------------

    @Transactional
    public ArtefactDetail shareArtefact(
            @ToolArg(name = "key", description = "Unique key for this artefact") String key,
            @ToolArg(name = "description", description = "Human-readable description", required = false) String description,
            @ToolArg(name = "created_by", description = "Owner instance identifier") String createdBy,
            @ToolArg(name = "content", description = "Content to store or append") String content,
            @ToolArg(name = "append", description = "Append to existing content (default false)", required = false) Boolean append,
            @ToolArg(name = "last_chunk", description = "Mark artefact complete (default true)", required = false) Boolean lastChunk) {
        boolean doAppend = append != null && append;
        boolean isLastChunk = lastChunk == null || lastChunk;
        var data = dataService.store(key, description, createdBy, content, doAppend, isLastChunk);
        return toArtefactDetail(data);
    }

    @Transactional
    public ArtefactDetail beginArtefact(
            @ToolArg(name = "key", description = "Unique key for this artefact") String key,
            @ToolArg(name = "description", description = "Human-readable description", required = false) String description,
            @ToolArg(name = "created_by", description = "Owner instance identifier") String createdBy,
            @ToolArg(name = "content", description = "First chunk of content") String content) {
        var data = dataService.store(key, description, createdBy, content, false, false);
        return toArtefactDetail(data);
    }

    @Transactional
    public ArtefactDetail appendChunk(
            @ToolArg(name = "key", description = "Artefact key (from begin_artefact)") String key,
            @ToolArg(name = "content", description = "Content chunk to append") String content) {
        var data = dataService.store(key, null, null, content, true, false);
        return toArtefactDetail(data);
    }

    @Transactional
    public ArtefactDetail finalizeArtefact(
            @ToolArg(name = "key", description = "Artefact key (from begin_artefact)") String key,
            @ToolArg(name = "content", description = "Optional final chunk of content to append", required = false) String content) {
        String chunk = content != null ? content : "";
        var data = dataService.store(key, null, null, chunk, true, true);
        return toArtefactDetail(data);
    }

    public ArtefactDetail getArtefact(
            @ToolArg(name = "key", description = "Artefact key", required = false) String key,
            @ToolArg(name = "id", description = "Artefact UUID", required = false) String id) {
        boolean hasKey = key != null && !key.isBlank();
        boolean hasId = id != null && !id.isBlank();
        if (!hasKey && !hasId) {
            throw new IllegalArgumentException("Either 'key' or 'id' must be provided");
        }
        var data = hasKey
                ? dataService.getByKey(key)
                        .orElseThrow(() -> new IllegalArgumentException("Artefact not found: key=" + key))
                : dataService.getByUuid(java.util.UUID.fromString(id))
                        .orElseThrow(() -> new IllegalArgumentException("Artefact not found: id=" + id));
        return toArtefactDetail(data);
    }

    public java.util.List<io.casehub.qhorus.api.message.ArtefactRef> getArtefactRefs(
            @ToolArg(name = "message_id", description = "Message ID") Long messageId) {
        io.casehub.qhorus.api.message.Message msg = messageStore.find(messageId)
                                                                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        return msg.artefactRefs() != null ? msg.artefactRefs() : java.util.List.of();
    }


    public List<ArtefactDetail> listArtefacts() {
        return dataService.listAll().stream().map(this::toArtefactDetail).toList();
    }

    @Transactional
    public String claimArtefact(
            @ToolArg(name = "artefact_id", description = "Artefact UUID") String artefactId,
            @ToolArg(name = "instance_id", description = "Claiming instance UUID") String instanceId) {
        try {
            dataService.claim(java.util.UUID.fromString(artefactId), java.util.UUID.fromString(instanceId));
            return "claimed";
        } catch (final IllegalArgumentException | IllegalStateException e) {
            return toolError(e);
        }
    }

    @Transactional
    public String releaseArtefact(
            @ToolArg(name = "artefact_id", description = "Artefact UUID") String artefactId,
            @ToolArg(name = "instance_id", description = "Releasing instance UUID") String instanceId) {
        try {
            dataService.release(java.util.UUID.fromString(artefactId), java.util.UUID.fromString(instanceId));
            return "released";
        } catch (final IllegalArgumentException | IllegalStateException e) {
            return toolError(e);
        }
    }

    /** Not a @Tool — helper for tests and internal GC logic. */
    public boolean isGcEligible(String artefactId) {
        return dataService.isGcEligible(java.util.UUID.fromString(artefactId));
    }

    @Transactional
    public RevokeResult revokeArtefact(
            @ToolArg(name = "artefact_id", description = "UUID of the artefact to revoke") String artefactId) {
        java.util.UUID   uuid = java.util.UUID.fromString(artefactId);
        SharedData data = dataStore.find(uuid).orElse(null);
        if (data == null) {
            return new RevokeResult(artefactId, null, null, 0, 0, false,
                    "Artefact not found: " + artefactId);
        }
        String key = data.key();
        String createdBy = data.createdBy();
        long sizeBytes = data.sizeBytes();

        int claimsReleased = dataStore.countClaims(uuid);
        dataStore.delete(uuid);

        return new RevokeResult(artefactId, key, createdBy, sizeBytes, claimsReleased, true,
                "Artefact '" + key + "' revoked — " + claimsReleased + " claim(s) released");
    }

    // ---------------------------------------------------------------------------
    // Human-in-the-loop — message and instance management
    // ---------------------------------------------------------------------------

    @Tool(name = "delete_message", description = "Delete a single message by its sequence ID. "
            + "Use for PII removal, bad data, or agent mistakes. Does not cascade to replies.")
    @Transactional
    public DeleteMessageResult deleteMessage(
            @ToolArg(name = "message_id", description = "Sequence ID of the message to delete") Long messageId) {
        Message msg = messageStore.find(messageId).orElse(null);
        if (msg == null) {
            return new DeleteMessageResult(messageId, false, null, null, null,
                    "Message not found: " + messageId);
        }
        String sender = msg.sender();
        String type = msg.messageType().name();
        String preview = msg.content() != null
                ? (msg.content().length() > 80 ? msg.content().substring(0, 80) + "…" : msg.content())
                : null;
        // Orphan replies (null out in_reply_to) before deleting — replies survive, FK satisfied
        messageStore.scan(MessageQuery.builder().inReplyTo(messageId).build())
                .forEach(reply -> messageStore.put(reply.toBuilder().inReplyTo(null).build()));
        // Post audit event to the channel
        messageService.dispatch(MessageDispatch.builder()
                .channelId(msg.channelId()).sender("system").type(MessageType.EVENT)
                .actorType(ActorType.SYSTEM).build());
        messageStore.delete(msg.id());
        return new DeleteMessageResult(messageId, true, sender, type, preview,
                "Message " + messageId + " deleted");
    }

    // ---------------------------------------------------------------------------
    // Ledger audit trail tools
    // ---------------------------------------------------------------------------

    /** Backward-compat overload — no correlation_id or sort. Used by existing tests. */
    List<Map<String, Object>> listLedgerEntries(String channel, String typeFilter,
            String agentId, String since, Long afterId, int limit) {
        return listLedgerEntries(channel, typeFilter, agentId, since, afterId,
                null, null, limit);
    }

    @Transactional
    public List<Map<String, Object>> listLedgerEntries(
            @ToolArg(name = "channel", description = "Channel name or UUID") String channel,
            @ToolArg(name = "type_filter", description = "Comma-separated MessageType names to include "
                    + "(e.g. 'COMMAND,DONE,FAILURE'). Omit to return all types.", required = false) String typeFilter,
            @ToolArg(name = "sender", description = "Filter by sender — returns only entries from this agent", required = false) String agentId,
            @ToolArg(name = "since", description = "ISO-8601 timestamp — return only entries at or after this time", required = false) String since,
            @ToolArg(name = "after_id", description = "Return entries with sequence_number > after_id (cursor pagination)", required = false) Long afterId,
            @ToolArg(name = "correlation_id", description = "Filter by correlation ID — returns only entries for this obligation", required = false) String correlationId,
            @ToolArg(name = "sort", description = "Sort order: 'asc' (default, oldest first) or 'desc' (newest first)", required = false) String sort,
            @ToolArg(name = "limit", description = "Maximum entries to return (default 20, max 100)", required = false) Integer limit) {

        final Channel ch = resolveChannel(channel);

        java.util.Set<String> types = null;
        if (typeFilter != null && !typeFilter.isBlank()) {
            types = java.util.Arrays.stream(typeFilter.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toSet());
        }

        final int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 100) : 20;

        java.time.Instant sinceInstant = null;
        if (since != null && !since.isBlank()) {
            try {
                sinceInstant = java.time.Instant.parse(since);
            } catch (final java.time.format.DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Invalid 'since' timestamp '" + since + "' — use ISO-8601 format, e.g. 2026-04-15T10:00:00Z");
            }
        }

        final boolean sortDesc;
        if (sort == null || sort.isBlank() || "asc".equalsIgnoreCase(sort)) {
            sortDesc = false;
        } else if ("desc".equalsIgnoreCase(sort)) {
            sortDesc = true;
        } else {
            throw new IllegalArgumentException(
                    "Invalid sort value '" + sort + "' — use 'asc' or 'desc'");
        }

        final List<MessageLedgerEntry> entries = ledgerRepo.listEntries(
                ch.id(), types, afterId, agentId, sinceInstant, correlationId, sortDesc, effectiveLimit,
                currentPrincipal.tenancyId());

        return entries.stream().map(this::toLedgerEntryMap).toList();
    }

    @Transactional
    public ObligationChainSummary getObligationChain(
            @ToolArg(name = "channel", description = "Channel name or UUID") String channel,
            @ToolArg(name = "correlation_id", description = "Correlation ID of the obligation to inspect") String correlationId) {

        final Channel ch = resolveChannel(channel);

        final List<MessageLedgerEntry> chain = ledgerRepo.findAllByCorrelationId(ch.id(), correlationId, currentPrincipal.tenancyId());

        if (chain.isEmpty()) {
            return new ObligationChainSummary(correlationId, null, null, null, null, null,
                    List.of(), 0, null);
        }

        final MessageLedgerEntry first = chain.get(0);
        final String initiator = first.actorId;
        final String createdAt = first.occurredAt != null ? first.occurredAt.toString() : null;

        // Terminal entry: first DONE / FAILURE / DECLINE (not HANDOFF — that is delegated, not resolved)
        final java.util.Set<String> terminal = java.util.Set.of("DONE", "FAILURE", "DECLINE");
        final MessageLedgerEntry terminalEntry = chain.stream()
                .filter(e -> terminal.contains(e.messageType))
                .findFirst()
                .orElse(null);

        final String resolution = terminalEntry != null ? terminalEntry.messageType : null;
        final String resolvedAt = (terminalEntry != null && terminalEntry.occurredAt != null)
                ? terminalEntry.occurredAt.toString()
                : null;
        final Long elapsedSeconds = (terminalEntry != null && first.occurredAt != null
                && terminalEntry.occurredAt != null)
                        ? terminalEntry.occurredAt.getEpochSecond() - first.occurredAt.getEpochSecond()
                        : null;

        // Participants — unique actorIds in encounter order
        final List<String> participants = chain.stream()
                .map(e -> e.actorId)
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        final int handoffCount = (int) chain.stream()
                .filter(e -> "HANDOFF".equals(e.messageType))
                .count();

        final CommitmentDetail commitment = commitmentStore.findByCorrelationId(correlationId)
                .map(CommitmentDetail::from)
                .orElse(null);

        return new ObligationChainSummary(correlationId, initiator, createdAt, resolvedAt,
                elapsedSeconds, resolution, participants, handoffCount, commitment);
    }

    @Transactional
    public List<CausalChainEntry> getCausalChain(
            @ToolArg(name = "channel", description = "Channel name or UUID. When omitted, walks across channel boundaries.", required = false) String channel,
            @ToolArg(name = "ledger_entry_id", description = "UUID of the ledger entry (from list_ledger_entries entry_id field)") String ledgerEntryId) {

        final UUID entryUuid;
        try {
            entryUuid = UUID.fromString(ledgerEntryId);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid ledger_entry_id '" + ledgerEntryId + "' — must be a UUID");
        }

        final String tenancyId = currentPrincipal.tenancyId();

        if (channel != null && !channel.isBlank()) {
            final Channel ch = resolveChannel(channel);
            return ledgerRepo.findAncestorChain(ch.id(), entryUuid, tenancyId).stream()
                    .map(e -> new CausalChainEntry(
                            e.id != null ? e.id.toString() : null,
                            e.channelId != null ? e.channelId.toString() : null,
                            ch.name(),
                            e.messageType,
                            e.actorId,
                            e.correlationId,
                            e.occurredAt != null ? e.occurredAt.toString() : null,
                            e.content,
                            e.causedByEntryId != null ? e.causedByEntryId.toString() : null))
                    .toList();
        }

        final List<io.casehub.qhorus.runtime.ledger.MessageLedgerEntry> chain =
                ledgerRepo.findAncestorChainCrossChannel(entryUuid, tenancyId);

        final java.util.Set<java.util.UUID> channelIds = chain.stream()
                .map(e -> e.channelId)
                .collect(java.util.stream.Collectors.toSet());
        final java.util.Map<java.util.UUID, String> channelNames = channelStore.findByIds(channelIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        c -> c.id(), c -> c.name(), (a, b) -> a));

        return chain.stream()
                .map(e -> new CausalChainEntry(
                        e.id != null ? e.id.toString() : null,
                        e.channelId != null ? e.channelId.toString() : null,
                        channelNames.getOrDefault(e.channelId, "unknown"),
                        e.messageType,
                        e.actorId,
                        e.correlationId,
                        e.occurredAt != null ? e.occurredAt.toString() : null,
                        e.content,
                        e.causedByEntryId != null ? e.causedByEntryId.toString() : null))
                .toList();
    }

    @Transactional
    public io.casehub.qhorus.runtime.ledger.CausalGraphService.CausalGraph getCausalGraph(
            @ToolArg(name = "correlation_id", description = "Correlation ID to trace across all channels") String correlationId,
            @ToolArg(name = "limit", description = "Maximum entries to include (default 100, max 500)", required = false) Integer limit) {

        final int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 500) : 100;
        return causalGraphService.buildGraph(correlationId, effectiveLimit, currentPrincipal.tenancyId());
    }

    @Transactional
    public String renderCausalGraph(
            @ToolArg(name = "correlation_id", description = "Correlation ID to trace across all channels") String correlationId,
            @ToolArg(name = "limit", description = "Maximum entries to include (default 200, max 500)", required = false) Integer limit) {
        final int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 500) : 200;
        var       graph          = causalGraphService.buildGraph(correlationId, effectiveLimit, currentPrincipal.tenancyId());
        return io.casehub.qhorus.runtime.ledger.CausalGraphRenderer.render(graph);
    }


    @Transactional
    public List<StalledObligation> listStalledObligations(
            @ToolArg(name = "channel", description = "Channel name or UUID") String channel,
            @ToolArg(name = "older_than_seconds", description = "Minimum age in seconds to consider stalled (default 30)", required = false) Integer olderThanSeconds) {

        final Channel ch = resolveChannel(channel);

        final int threshold = olderThanSeconds != null ? olderThanSeconds : 30;
        final java.time.Instant cutoff = java.time.Instant.now().minusSeconds(threshold);
        final java.time.Instant now = java.time.Instant.now();

        return ledgerRepo.findStalledCommands(ch.id(), cutoff, currentPrincipal.tenancyId()).stream()
                .map(e -> {
                    final long stalledFor = e.occurredAt != null
                            ? now.getEpochSecond() - e.occurredAt.getEpochSecond()
                            : 0L;
                    return new StalledObligation(
                            e.correlationId,
                            e.actorId,
                            e.content,
                            e.occurredAt != null ? e.occurredAt.toString() : null,
                            stalledFor);
                })
                .toList();
    }

    @Transactional
    public ObligationStats getObligationStats(
            @ToolArg(name = "channel", description = "Channel name or UUID") String channel) {

        final Channel ch = resolveChannel(channel);

        final Map<String, Long> counts = ledgerRepo.countByOutcome(ch.id(), currentPrincipal.tenancyId());
        final long total = counts.getOrDefault("COMMAND", 0L);
        final long fulfilled = counts.getOrDefault("DONE", 0L);
        final long failed = counts.getOrDefault("FAILURE", 0L);
        final long declined = counts.getOrDefault("DECLINE", 0L);
        final long delegated = counts.getOrDefault("HANDOFF", 0L);
        final long stillOpen = Math.max(0L, total - fulfilled - failed - declined - delegated);
        final long stalled = ledgerRepo
                .findStalledCommands(ch.id(), java.time.Instant.now().minusSeconds(30), currentPrincipal.tenancyId())
                .size();
        final double rate = total > 0 ? (double) fulfilled / total : 0.0;

        return new ObligationStats((int) total, (int) fulfilled, (int) failed, (int) declined,
                (int) delegated, (int) stillOpen, (int) stalled, rate);
    }

    @Transactional
    public TelemetrySummary getTelemetrySummary(
            @ToolArg(name = "channel", description = "Channel name or UUID") String channel,
            @ToolArg(name = "since", description = "ISO-8601 timestamp — include only events at or after this time", required = false) String since) {

        final Channel ch = resolveChannel(channel);

        java.time.Instant sinceInstant = null;
        if (since != null && !since.isBlank()) {
            try {
                sinceInstant = java.time.Instant.parse(since);
            } catch (final java.time.format.DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Invalid 'since' timestamp '" + since + "' — use ISO-8601 format");
            }
        }

        final List<MessageLedgerEntry> events = ledgerRepo.findEventsSince(ch.id(), sinceInstant, currentPrincipal.tenancyId());

        if (events.isEmpty()) {
            return new TelemetrySummary(0, Map.of(), 0L, 0L);
        }

        // Aggregate per tool (null toolName is a valid key)
        final java.util.LinkedHashMap<String, long[]> agg = new java.util.LinkedHashMap<>();
        for (final MessageLedgerEntry e : events) {
            final long[] acc = agg.computeIfAbsent(e.toolName, k -> new long[3]);
            acc[0]++; // count
            acc[1] += e.durationMs != null ? e.durationMs : 0; // total duration
            acc[2] += e.tokenCount != null ? e.tokenCount : 0; // total tokens
        }

        final Map<String, ToolTelemetry> byTool = new java.util.LinkedHashMap<>();
        for (final var entry : agg.entrySet()) {
            final long[] acc = entry.getValue();
            byTool.put(entry.getKey(),
                    new ToolTelemetry((int) acc[0], acc[0] > 0 ? acc[1] / acc[0] : 0L, acc[2]));
        }

        final long totalTokens = events.stream()
                .mapToLong(e -> e.tokenCount != null ? e.tokenCount : 0L).sum();
        final long totalDuration = events.stream()
                .mapToLong(e -> e.durationMs != null ? e.durationMs : 0L).sum();

        return new TelemetrySummary(events.size(), byTool, totalTokens, totalDuration);
    }

    @Transactional
    public List<Map<String, Object>> getChannelTimeline(
            @ToolArg(name = "channel", description = "Channel name or UUID") String channel,
            @ToolArg(name = "after_id", description = "Return messages with id > after_id (cursor pagination)", required = false) Long afterId,
            @ToolArg(name = "limit", description = "Maximum messages to return (default 50, max 200)", required = false) Integer limit) {

        Channel ch = resolveChannel(channel);

        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 200) : 50;

        List<Message> messages = messageStore.scan(
                MessageQuery.poll(ch.id(), afterId, effectiveLimit));

        // Batch-fetch ledger entries for all EVENT messages in one IN query. Refs #262.
        final List<Long> eventIds = messages.stream()
                .filter(m -> m.messageType() == MessageType.EVENT)
                .map(m -> m.id())
                .toList();
        final Map<Long, MessageLedgerEntry> ledgerByMessageId = eventIds.isEmpty()
                ? Map.of()
                : ledgerRepo.findByMessageIds(eventIds).stream()
                        .collect(Collectors.toMap(e -> e.messageId, e -> e));

        return messages.stream()
                .map(m -> entityMapper.toTimelineEntry(m,
                        m.messageType() == MessageType.EVENT ? ledgerByMessageId.get(m.id()) : null))
                .toList();
    }

    @Transactional
    public List<Map<String, Object>> getObligationActivity(
            @ToolArg(name = "correlation_id", description = "Correlation ID of the obligation to trace across channels") String correlationId,
            @ToolArg(name = "include_content_search", description = "Deprecated — reserved for future use. Has no effect.", required = false) Boolean includeContentSearch,
            @ToolArg(name = "limit", description = "Maximum entries to return (default 100, max 500)", required = false) Integer limit) {

        final int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 500) : 100;

        final List<io.casehub.qhorus.runtime.ledger.MessageLedgerEntry> entries =
                ledgerRepo.findByCorrelationIdAcrossChannels(correlationId, effectiveLimit, currentPrincipal.tenancyId());

        if (entries.isEmpty()) {
            return List.of();
        }

        // Batch-load channel names from the unique channel IDs in the results
        final java.util.Set<java.util.UUID> channelIds = entries.stream()
                .map(e -> e.channelId)
                .collect(java.util.stream.Collectors.toSet());
        final java.util.Map<java.util.UUID, String> channelNameById = channelService.listAll().stream()
                .filter(ch -> channelIds.contains(ch.id()))
                .collect(java.util.stream.Collectors.toMap(ch -> ch.id(), ch -> ch.name()));

        return entries.stream()
                .map(e -> toLedgerEntryMapWithChannel(e,
                        channelNameById.getOrDefault(e.channelId, "unknown")))
                .toList();
    }

    // ---------------------------------------------------------------------------
    // Human-in-the-loop — watchdogs and alerts (optional module)
    // ---------------------------------------------------------------------------

    private void requireWatchdogEnabled() {
        if (!qhorusConfig.watchdog().enabled()) {
            throw new IllegalStateException(
                    "Watchdog module is disabled. Set casehub.qhorus.watchdog.enabled=true to activate.");
        }
    }

    @Transactional
    public WatchdogSummary registerWatchdog(
            @ToolArg(name = "condition_type", description = "BARRIER_STUCK | APPROVAL_PENDING | AGENT_STALE | CHANNEL_IDLE | QUEUE_DEPTH | CONTEXT_PRESSURE | LOOP_DETECTED | OBLIGATION_FAN_OUT | CONVERSATION_STALL | ECHO_CHAMBER | DELIVERY_LAG") String conditionType,
            @ToolArg(name = "target_name", description = "Channel name, instance_id, or '*' for all") String targetName,
            @ToolArg(name = "threshold_seconds", description = "Time threshold in seconds (for time-based conditions)", required = false) Integer thresholdSeconds,
            @ToolArg(name = "threshold_count", description = "Count threshold (for QUEUE_DEPTH, LOOP_DETECTED repetitions, ECHO_CHAMBER min agents)", required = false) Integer thresholdCount,
            @ToolArg(name = "similarity_pct", description = "Content similarity percentage threshold 0-100 (for LOOP_DETECTED, ECHO_CHAMBER)", required = false) Integer similarityPct,
            @ToolArg(name = "notification_channel", description = "Channel to post alert events to") String notificationChannel,
            @ToolArg(name = "created_by", description = "Who is registering this watchdog") String createdBy,
            @ToolArg(name = "action", description = "Action on trigger: ALERT (default, notify only), PAUSE_CHANNEL (pause affected channel), DEREGISTER_AGENT (mark agent offline), QUARANTINE (pause + deregister + containment EVENT)", required = false) String action) {
        requireWatchdogEnabled();
        io.casehub.qhorus.api.watchdog.WatchdogConditionType type = io.casehub.qhorus.api.watchdog.WatchdogConditionType.fromString(conditionType)
                                                                                                                        .orElseThrow(() -> new IllegalArgumentException("Unknown condition_type '" + conditionType + "'. Valid: " + java.util.Arrays.toString(io.casehub.qhorus.api.watchdog.WatchdogConditionType.values())));
        io.casehub.qhorus.api.watchdog.WatchdogAction parsedAction = io.casehub.qhorus.api.watchdog.WatchdogAction.ALERT;
        if (action != null && !action.isBlank()) {
            try {
                parsedAction = io.casehub.qhorus.api.watchdog.WatchdogAction.valueOf(action);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown action '" + action + "'. Valid: " + java.util.Arrays.toString(io.casehub.qhorus.api.watchdog.WatchdogAction.values()));
            }
        }
        Watchdog w = watchdogStore.put(Watchdog.builder(type, targetName)
                                               .thresholdSeconds(thresholdSeconds).thresholdCount(thresholdCount)
                                               .similarityPct(similarityPct)
                                               .notificationChannel(notificationChannel).createdBy(createdBy)
                                               .action(parsedAction)
                                               .tenancyId(currentPrincipal.tenancyId()).build());
        return toWatchdogSummary(w);
    }

    @Transactional
    public List<WatchdogSummary> listWatchdogs() {
        requireWatchdogEnabled();
        return watchdogStore.scan(io.casehub.qhorus.api.store.query.WatchdogQuery.all()).stream()
                .map(this::toWatchdogSummary)
                .toList();
    }

    @Transactional
    public DeleteWatchdogResult deleteWatchdog(
            @ToolArg(name = "watchdog_id", description = "UUID of the watchdog to delete") String watchdogId) {
        requireWatchdogEnabled();
        final UUID watchdogUuid;
        try {
            watchdogUuid = UUID.fromString(watchdogId);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid watchdog_id '" + watchdogId + "' — must be a UUID (e.g. 550e8400-e29b-41d4-a716-446655440000)");
        }
        boolean found = watchdogStore.find(watchdogUuid).isPresent();
        if (found) watchdogStore.delete(watchdogUuid);
        long deleted = found ? 1 : 0;
        if (deleted > 0) {
            return new DeleteWatchdogResult(watchdogId, true, "Watchdog " + watchdogId + " deleted");
        }
        return new DeleteWatchdogResult(watchdogId, false,
                "Watchdog not found: " + watchdogId);
    }

    // ---------------------------------------------------------------------------
    // Reaction tools
    // ---------------------------------------------------------------------------

    @Tool(name = "react", description = "Add an emoji reaction to a message. Idempotent — reacting twice is a no-op.")
    @Transactional
    public Reaction react(
            @ToolArg(name = "message_id", description = "ID of the message to react to") Long messageId,
            @ToolArg(name = "emoji", description = "Emoji character or shortcode") String emoji,
            @ToolArg(name = "actor_id", description = "Who is reacting. Defaults to caller identity.", required = false) String actorId) {
        String actor = actorId != null ? actorId : currentPrincipal.actorId();
        return reactionService.react(messageId, emoji, actor, currentPrincipal.tenancyId());
    }

    @Tool(name = "unreact", description = "Remove an emoji reaction from a message. Idempotent — unreacting when not reacted is a no-op.")
    @Transactional
    public ReactionResult unreact(
            @ToolArg(name = "message_id", description = "ID of the message") Long messageId,
            @ToolArg(name = "emoji", description = "Emoji character or shortcode") String emoji,
            @ToolArg(name = "actor_id", description = "Who is unreacting. Defaults to caller identity.", required = false) String actorId) {
        String actor = actorId != null ? actorId : currentPrincipal.actorId();
        boolean removed = reactionService.unreact(messageId, emoji, actor);
        return new ReactionResult(messageId, emoji, removed);
    }

    @Tool(name = "get_reactions", description = "Get all reactions for a message, grouped by emoji with actor lists")
    public List<ReactionGroup> getReactions(
            @ToolArg(name = "message_id", description = "ID of the message") Long messageId) {
        return reactionService.getReactions(messageId);
    }

    @Tool(name = "get_reactions_batch", description = "Get reactions for multiple messages in one call, grouped by emoji with actor lists per message")
    public Map<Long, List<ReactionGroup>> getReactionsBatch(
            @ToolArg(name = "message_ids", description = "List of message IDs to fetch reactions for (max 200)") List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            throw new IllegalArgumentException("message_ids must be non-null and non-empty");
        }
        if (messageIds.size() > 200) {
            throw new IllegalArgumentException("message_ids cannot exceed 200 entries");
        }
        return reactionService.getReactionsBatch(messageIds);
    }



    // ---------------------------------------------------------------------------
    // Projection tools
    // ---------------------------------------------------------------------------


}
