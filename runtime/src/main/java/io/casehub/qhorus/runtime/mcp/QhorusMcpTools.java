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
 * Test infrastructure — convenience methods used by integration tests.
 * MCP tool exposure is via @McpDomain APIs (ChannelsApi, MessagingApi, etc.).
 */
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

    public ChannelDetail createChannel(
            String name,
            String description,
            String semantic,
            String barrierContributors,
            String allowedWriters,
            String adminInstances,
            Integer rateLimitPerChannel,
            Integer rateLimitPerInstance,
            String allowedTypes,
            String deniedTypes,
            String spaceId,
            String reviewerIds,
            String protocols,
            String protocolParticipants,
            String inboundConnectorId,
            String externalKey,
            String outboundConnectorId,
            String outboundDestination,
            Boolean trackDelivery) {
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
    public ChannelDetail updateChannelBinding(
            String channel,
            String outboundConnectorId,
            String outboundDestination) {
        Channel ch = resolveChannel(channel);
        channelService.updateConnectorBinding(ch.id(), outboundConnectorId, outboundDestination);
        return toChannelDetail(ch, messageStore.countByChannel(ch.id()));
    }

    @Transactional
    public ChannelDetail setChannelRateLimits(
            String channel,
            Integer rateLimitPerChannel,
            Integer rateLimitPerInstance) {
        Channel resolved = resolveChannel(channel);
        Channel ch       = channelService.setRateLimits(resolved.id(), rateLimitPerChannel, rateLimitPerInstance);
        return toChannelDetail(ch, messageStore.countByChannel(ch.id()));
    }

    @Transactional
    public String setDeliveryTracking(
            String channel,
            Boolean tracking) {
        Channel ch = resolveChannel(channel);
        channelService.setTrackDelivery(ch.id(), tracking);
        Channel updated   = ch.toBuilder().trackDelivery(tracking).build();
        boolean effective = io.casehub.qhorus.runtime.channel.ChannelService.isDeliveryTrackingEnabled(updated);
        return "Delivery tracking " + (effective ? "enabled" : "disabled")
               + " on channel '" + ch.name() + "'";
    }


    @Transactional
    public ChannelDetail setChannelWriters(
            String channel,
            String allowedWriters) {
        Channel resolved = resolveChannel(channel);
        Channel ch       = channelService.setAllowedWriters(resolved.id(), splitCsv(allowedWriters));
        return toChannelDetail(ch, messageStore.countByChannel(ch.id()));
    }

    @Transactional
    public ChannelDetail setChannelAdmins(
            String channel,
            String adminInstances) {
        Channel resolved = resolveChannel(channel);
        Channel ch       = channelService.setAdminInstances(resolved.id(), splitCsv(adminInstances));
        return toChannelDetail(ch, messageStore.countByChannel(ch.id()));
    }

    @Transactional
    public ChannelDetail setChannelReviewers(
            String channel,
            String reviewerIds) {
        Channel resolved = resolveChannel(channel);
        Channel ch       = channelService.setReviewerInstances(resolved.id(), splitCsv(reviewerIds));
        return toChannelDetail(ch, messageStore.countByChannel(ch.id()));
    }

    public List<String> listProtocols() {
        return new java.util.ArrayList<>(protocolRegistry.allNames());
    }

    public ChannelDetail setChannelProtocols(
            String channel,
            String protocols) {
        Channel      ch           = resolveChannel(channel);
        List<String> protocolList = splitCsv(protocols);
        if (protocolList.contains("ROUND_ROBIN") && ch.protocolParticipants().isEmpty()) {
            throw new IllegalArgumentException(
                    "ROUND_ROBIN requires protocolParticipants — set them first with set_protocol_participants");
        }
        Channel updated = channelService.setProtocols(ch.id(), protocolList);
        return toChannelDetail(updated, messageStore.countByChannel(ch.id()));
    }

    public ChannelDetail setProtocolParticipants(
            String channel,
            String participants) {
        Channel ch      = resolveChannel(channel);
        Channel updated = channelService.setProtocolParticipants(ch.id(), splitCsv(participants));
        return toChannelDetail(updated, messageStore.countByChannel(ch.id()));
    }

    public java.util.Map<String, Object> getChannelProtocols(
            String channel) {
        Channel ch = resolveChannel(channel);
        return java.util.Map.of(
                "protocols", ch.protocols(),
                "protocol_participants", ch.protocolParticipants());
    }

    public ChannelDetail setEnforcementMode(
            String channel,
            String mode) {
        Channel                                       ch = resolveChannel(channel);
        io.casehub.qhorus.api.channel.EnforcementMode enforcementMode;
        try {
            enforcementMode = io.casehub.qhorus.api.channel.EnforcementMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid enforcement mode: " + mode
                                               + ". Valid values: ADVISORY, BLOCKING, QUARANTINE");
        }
        Channel updated = channelService.setEnforcementMode(ch.id(), enforcementMode);
        return toChannelDetail(updated, messageStore.countByChannel(ch.id()));
    }

    public ChannelDetail setEnforcementExclusions(
            String channel,
            String exclusions) {
        Channel      ch            = resolveChannel(channel);
        List<String> exclusionList = splitCsv(exclusions);
        Channel      updated       = channelService.setEnforcementExclusions(ch.id(), exclusionList);
        return toChannelDetail(updated, messageStore.countByChannel(ch.id()));
    }

    public java.util.Map<String, Object> getChannelEnforcement(
            String channel) {
        Channel      ch               = resolveChannel(channel);
        List<String> availableSources = new java.util.ArrayList<>();
        availableSources.add("TYPE_POLICY");
        availableSources.add("CORRELATION_INTEGRITY");
        availableSources.addAll(protocolRegistry.allNames());
        return java.util.Map.of(
                "enforcement_mode", ch.enforcementMode() != null ? ch.enforcementMode().name() : "ADVISORY",
                "enforcement_exclusions", ch.enforcementExclusions(),
                "available_sources", availableSources);
    }

    public ChannelDetail setRoutingConfig(
            String channel,
            Double trustThreshold) {
        Channel ch = resolveChannel(channel);
        if (trustThreshold != null && (trustThreshold < 0.0 || trustThreshold > 1.0)) {
            throw new IllegalArgumentException("trust_threshold must be between 0.0 and 1.0, got: " + trustThreshold);
        }
        Channel updated = channelService.setRoutingTrustThreshold(ch.id(), trustThreshold);
        return toChannelDetail(updated, messageStore.countByChannel(ch.id()));
    }

    public java.util.Map<String, Object> getRoutingConfig(
            String channel) {
        Channel                       ch                 = resolveChannel(channel);
        double                        effectiveThreshold = routingBridge.effectiveThreshold(ch);
        java.util.Map<String, Object> result             = new java.util.LinkedHashMap<>();
        result.put("channel", ch.name());
        result.put("trust_threshold", ch.routingTrustThreshold());
        result.put("effective_threshold", effectiveThreshold);
        result.put("global_default", qhorusConfig.routing().defaultTrustThreshold());
        return result;
    }

    public java.util.Map<String, Object> getRoutingCandidates(
            String capability,
            String channel) {
        Channel                         ch        = channel != null && !channel.isBlank() ? resolveChannel(channel) : null;
        String                          tenancyId = currentPrincipal.tenancyId();
        RoutingBridge.RoutingDiagnostic diag      = routingBridge.diagnose(capability, ch, tenancyId);

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("capability", capability);
        result.put("routing_available", diag.routingAvailable());
        result.put("effective_threshold", diag.effectiveThreshold());
        result.put("candidate_count", diag.candidates().size());
        result.put("candidates", diag.candidates().stream()
                                     .map(c -> java.util.Map.of(
                                             "agent_id", c.agentId(),
                                             "name", c.name() != null ? c.name() : c.agentId(),
                                             "trust_score", c.trustScore(),
                                             "passes_threshold", c.passesThreshold()))
                                     .toList());
        result.put("selection_outcome", diag.selectionOutcome());
        if (diag.selectedAgentId() != null) {
            result.put("selected_agent", diag.selectedAgentId());
            result.put("selected_trust_score", diag.selectedTrustScore());
        }
        if (diag.reason() != null) {
            result.put("reason", diag.reason());
        }
        return result;
    }


    @Transactional
    public Map<String, Object> attest(
            String entryId,
            String verdict,
            String evidence) {
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
            String entryId) {
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
            String entryId,
            String reviewerIds,
            String channel) {
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


    public ChannelDetail setChannelTypeConstraints(
            String channel,
            String allowedTypes,
            String deniedTypes) {
        Channel resolved = resolveChannel(channel);
        Channel ch = channelService.setTypeConstraints(resolved.id(),
                                                             MessageType.parseTypes(allowedTypes), MessageType.parseTypes(deniedTypes));
        return toChannelDetail(ch, messageStore.countByChannel(ch.id()));
    }

    public List<ChannelDetail> listChannels() {
        List<Channel> channels = channelService.listAll();
        if (channels.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long>                    countByChannel = messageStore.countAllByChannel();
        Map<UUID, ChannelConnectorBinding> allBindings    = bindingStore.findAll();
        Map<UUID, String>                  spaceNames     = buildSpaceNameMap(channels);
        return channels.stream()
                       .map(ch -> toChannelDetail(ch, countByChannel.getOrDefault(ch.id(), 0L), allBindings, spaceNames))
                       .toList();}

    public List<ChannelDetail> findChannel(
            String keyword) {
        List<Channel> matches = channelStore.scan(ChannelQuery.byKeyword(keyword));
        return matches.stream()
                .map(ch -> toChannelDetail(ch, messageStore.countByChannel(ch.id())))
                .toList();
    }

    // ---------------------------------------------------------------------------
    // Human-in-the-loop — channel flow control
    // ---------------------------------------------------------------------------

    /** Convenience overload — no caller identity (open governance assumed). */
    ChannelDetail pauseChannel(String channel) {
        return pauseChannel(channel, null);
    }

    @Transactional
    public ChannelDetail pauseChannel(
            String channel,
            String callerInstanceId) {
        Channel ch = resolveChannel(channel);
        checkAdminAccess(ch, callerInstanceId, "pause_channel");
        ch = channelService.pause(ch.id());
        return toChannelDetail(ch, messageStore.countByChannel(ch.id()));
    }

    /** Convenience overload — no caller identity (open governance assumed). */
    ChannelDetail resumeChannel(String channel) {
        return resumeChannel(channel, null);
    }

    @Transactional
    public ChannelDetail resumeChannel(
            String channel,
            String callerInstanceId) {
        Channel ch = resolveChannel(channel);
        checkAdminAccess(ch, callerInstanceId, "resume_channel");
        ch = channelService.resume(ch.id());
        return toChannelDetail(ch, messageStore.countByChannel(ch.id()));
    }

    /** Convenience overload — no caller identity (open governance assumed). */
    DeleteChannelResult deleteChannel(String channel, Boolean force) {
        return deleteChannel(channel, force, null);
    }

    @Transactional
    public DeleteChannelResult deleteChannel(
            String channel,
            Boolean force,
            String callerInstanceId) {
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


    @Transactional
    public DispatchResult sendMessage(
            String channel,
            String sender,
            String type,
            String content,
            String payload,
            String correlationId,
            Long inReplyTo,
            List<String> artefactRefs,
            String target,
            String deadline,
            String subjectId,
            String causedByEntryId,
            String topic) {
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

    /** Backward-compat overload — no reader_instance_id filter, no include_events. */
    CheckResult checkMessages(String channelName, Long afterId, Integer limit, String sender) {
        return checkMessages(channelName, afterId, limit, sender, null, null);
    }

    /** Backward-compat overload — no include_events. */
    CheckResult checkMessages(String channelName, Long afterId, Integer limit, String sender,
            String readerInstanceId) {
        return checkMessages(channelName, afterId, limit, sender, readerInstanceId, null);
    }

    @Transactional
    public CheckResult checkMessages(
            String channel,
            Long afterId,
            Integer limit,
            String sender,
            String readerInstanceId,
            Boolean includeEvents) {
        Channel ch = resolveChannel(channel);

        if (ch.paused()) {
            return new CheckResult(List.of(), afterId != null ? afterId : 0L, "Channel is paused");
        }

        long cursor = afterId != null ? afterId : 0L;
        int pageSize = limit != null ? limit : 20;
        boolean events = includeEvents != null && includeEvents;

        return switch (ch.semantic()) {
            case EPHEMERAL -> checkMessagesEphemeral(ch, cursor, pageSize, readerInstanceId);
            case COLLECT -> checkMessagesCollect(ch, readerInstanceId);
            case BARRIER -> checkMessagesBarrier(ch, readerInstanceId);
            default -> checkMessagesAppend(ch, cursor, pageSize, sender, readerInstanceId, events);
        };
    }


    private void advanceDeliveryCursorIfTracked(Channel ch, String readerInstanceId, Long lastId) {
        if (lastId == null || lastId <= 0) {return;}
        if (readerInstanceId == null || readerInstanceId.isBlank()) {return;}
        if (!io.casehub.qhorus.runtime.channel.ChannelService.isDeliveryTrackingEnabled(ch)) {return;}
        membershipStore.updateLastDeliveredMessageId(ch.id(), readerInstanceId, lastId);
    }

    /** EPHEMERAL: deliver messages visible to this reader then delete only those. */
    private CheckResult checkMessagesEphemeral(Channel ch, long cursor, int pageSize, String readerInstanceId) {
        List<Message> fetched = messageService.pollAfter(ch.id(), cursor, pageSize);
        List<Message> visible = fetched.stream()
                                       .filter(m -> isVisibleToReader(m, readerInstanceId,
                                                                      () -> instanceService.findCapabilityTagsForInstance(readerInstanceId)))
                                       .toList();
        List<MessageSummary> summaries = visible.stream().map(this::toMessageSummary).toList();
        Long                 lastId    = summaries.isEmpty() ? cursor : summaries.getLast().messageId();
        advanceDeliveryCursorIfTracked(ch, readerInstanceId, lastId);
        if (!visible.isEmpty()) {
            List<Long> ids = visible.stream().map(m -> m.id()).toList();
            ids.forEach(messageStore::delete);
        }
        return new CheckResult(summaries, lastId, null);}

    /** COLLECT: deliver ALL accumulated messages atomically and clear the channel; filter returned view. */
    private CheckResult checkMessagesCollect(Channel ch, String readerInstanceId) {
        List<Message> messages = messageStore.scan(MessageQuery.builder()
                                                               .channelId(ch.id()).excludeTypes(List.of(MessageType.EVENT)).build());
        List<MessageSummary> summaries = messages.stream()
                                                 .filter(m -> isVisibleToReader(m, readerInstanceId,
                                                                                () -> instanceService.findCapabilityTagsForInstance(readerInstanceId)))
                                                 .map(this::toMessageSummary).toList();
        Long lastId = summaries.isEmpty() ? 0L : summaries.getLast().messageId();
        advanceDeliveryCursorIfTracked(ch, readerInstanceId, lastId);
        if (!messages.isEmpty()) {
            messageStore.deleteNonEvent(ch.id());
        }
        return new CheckResult(summaries, lastId, null);}

    /** BARRIER: block until all declared contributors have written; then deliver and reset. */
    private CheckResult checkMessagesBarrier(Channel ch, String readerInstanceId) {
        Set<String> required = ch.barrierContributors() != null
                               ? new java.util.HashSet<>(ch.barrierContributors())
                               : Set.of();

        if (required.isEmpty()) {
            return new CheckResult(List.of(), 0L, "Waiting for: (no contributors declared — check channel configuration)");
        }

        List<String> written = messageStore.distinctSendersByChannel(ch.id(), MessageType.EVENT);

        Set<String> pending = required.stream()
                                      .filter(r -> !written.contains(r))
                                      .collect(Collectors.toSet());

        if (!pending.isEmpty()) {
            String status = "Waiting for: " + String.join(", ", pending.stream().sorted().toList());
            return new CheckResult(List.of(), 0L, status);
        }

        List<Message> messages = messageStore.scan(MessageQuery.builder()
                                                               .channelId(ch.id()).excludeTypes(List.of(MessageType.EVENT)).build());
        List<MessageSummary> summaries = messages.stream()
                                                 .filter(m -> isVisibleToReader(m, readerInstanceId,
                                                                                () -> instanceService.findCapabilityTagsForInstance(readerInstanceId)))
                                                 .map(this::toMessageSummary).toList();
        Long lastId = summaries.isEmpty() ? 0L : summaries.getLast().messageId();
        advanceDeliveryCursorIfTracked(ch, readerInstanceId, lastId);
        messageStore.deleteNonEvent(ch.id());
        return new CheckResult(summaries, lastId, null);}

    /** APPEND / LAST_WRITE: standard cursor-based polling with optional target filter. */
    private CheckResult checkMessagesAppend(Channel ch, long cursor, int pageSize, String sender,
                                            String readerInstanceId, boolean includeEvents) {
        List<Message> messages = (sender != null && !sender.isBlank())
                                 ? messageService.pollAfterBySender(ch.id(), cursor, pageSize, sender, includeEvents)
                                 : messageService.pollAfter(ch.id(), cursor, pageSize, includeEvents);
        List<MessageSummary> summaries = messages.stream()
                                                 .filter(m -> isVisibleToReader(m, readerInstanceId,
                                                                                () -> instanceService.findCapabilityTagsForInstance(readerInstanceId)))
                                                 .map(this::toMessageSummary).toList();
        Long lastId = summaries.isEmpty() ? cursor : summaries.getLast().messageId();
        advanceDeliveryCursorIfTracked(ch, readerInstanceId, lastId);
        return new CheckResult(summaries, lastId, null);}

    /** Backward-compat overload — no reader_instance_id filter. */
    List<MessageSummary> getReplies(Long messageId) {
        return getReplies(messageId, null, null, null);
    }

    List<MessageSummary> getReplies(Long messageId, String readerInstanceId) {
        return getReplies(messageId, readerInstanceId, null, null);
    }

    @Transactional
    public List<MessageSummary> getReplies(
            Long messageId,
            String readerInstanceId,
            Long afterId,
            Integer limit) {
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

    public List<MessageSummary> searchMessages(
            String query,
            String channel,
            Integer limit,
            String readerInstanceId) {
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

    public MessageSummary getMessage(
            Long messageId) {
        Message message = messageService.findById(messageId)
                                              .orElseThrow(() -> new IllegalArgumentException(
                        "Message not found: " + messageId));
        return toMessageSummary(message);
    }

    // ---------------------------------------------------------------------------
    // Correlation / wait_for_reply
    // ---------------------------------------------------------------------------

    public WaitResult waitForReply(
            String channel,
            String correlationId,
            Integer timeoutS,
            String instanceId) {
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

    public WaitResult requestApproval(
            String channel,
            String content,
            Integer timeoutS) {
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

    @Transactional
    public DispatchResult respondToApproval(
            String correlationId,
            String responseText,
            String channel) {
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

    @Transactional
    public CancelWaitResult cancelWait(
            String correlationId) {
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
            String channel,
            String sender,
            String role) {
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
            String correlationId) {
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
            String key,
            String description,
            String createdBy,
            String content,
            Boolean append,
            Boolean lastChunk) {
        boolean doAppend = append != null && append;
        boolean isLastChunk = lastChunk == null || lastChunk;
        var data = dataService.store(key, description, createdBy, content, doAppend, isLastChunk);
        return toArtefactDetail(data);
    }

    @Transactional
    public ArtefactDetail beginArtefact(
            String key,
            String description,
            String createdBy,
            String content) {
        var data = dataService.store(key, description, createdBy, content, false, false);
        return toArtefactDetail(data);
    }

    @Transactional
    public ArtefactDetail appendChunk(
            String key,
            String content) {
        var data = dataService.store(key, null, null, content, true, false);
        return toArtefactDetail(data);
    }

    @Transactional
    public ArtefactDetail finalizeArtefact(
            String key,
            String content) {
        String chunk = content != null ? content : "";
        var data = dataService.store(key, null, null, chunk, true, true);
        return toArtefactDetail(data);
    }

    public ArtefactDetail getArtefact(
            String key,
            String id) {
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
            Long messageId) {
        io.casehub.qhorus.api.message.Message msg = messageStore.find(messageId)
                                                                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        return msg.artefactRefs() != null ? msg.artefactRefs() : java.util.List.of();
    }


    public List<ArtefactDetail> listArtefacts() {
        return dataService.listAll().stream().map(this::toArtefactDetail).toList();
    }

    @Transactional
    public String claimArtefact(
            String artefactId,
            String instanceId) {
        try {
            dataService.claim(java.util.UUID.fromString(artefactId), java.util.UUID.fromString(instanceId));
            return "claimed";
        } catch (final IllegalArgumentException | IllegalStateException e) {
            return toolError(e);
        }
    }

    @Transactional
    public String releaseArtefact(
            String artefactId,
            String instanceId) {
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
            String artefactId) {
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

    @Transactional
    public DeleteMessageResult deleteMessage(
            Long messageId) {
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

    /** Convenience overload — no caller identity (open governance assumed). */
    ClearChannelResult clearChannel(String channel) {
        return clearChannel(channel, null);
    }

    @Transactional
    public ClearChannelResult clearChannel(
            String channel,
            String callerInstanceId) {
        Channel ch = resolveChannel(channel);
        checkAdminAccess(ch, callerInstanceId, "clear_channel");
        long deleted = messageStore.scan(MessageQuery.builder()
                .channelId(ch.id()).excludeTypes(List.of(MessageType.EVENT)).build()).size();
        messageStore.deleteNonEvent(ch.id());
        // Post audit event (survives the clear)
        messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id()).sender("system").type(MessageType.EVENT)
                .actorType(ActorType.SYSTEM).build());
        channelService.updateLastActivity(ch.id(), ch.tenancyId());
        return new ClearChannelResult(ch.name(), (int) deleted, true);
    }

    @Transactional
    public ChannelDigest channelDigest(
            String channel,
            Integer limit) {
        Channel ch = resolveChannel(channel);

        int pageSize = limit != null ? limit : 10;
        List<Message> allMessages = messageStore.scan(MessageQuery.builder()
                                                                  .channelId(ch.id()).excludeTypes(List.of(MessageType.EVENT)).build());

        // Topic resolved status from Topic records
        Map<String, io.casehub.qhorus.api.message.TopicSummary> topicSummaries =
                topicService.listTopics(ch.id()).stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    io.casehub.qhorus.api.message.TopicSummary::name, t -> t, (a, b) -> a));

        if (allMessages.isEmpty()) {
            List<TopicDigest> emptyTopics = topicSummaries.values().stream()
                                                          .map(t -> new TopicDigest(t.name(), 0,
                                                                                    t.lastActivityAt() != null ? t.lastActivityAt().toString() : null,
                                                                                    t.resolved(), t.resolvedAt() != null ? t.resolvedAt().toString() : null))
                                                          .toList();
            return new ChannelDigest(ch.name(), ch.semantic().name(), ch.paused(),
                                     0L, Map.of(), Map.of(), 0, List.of(), List.of(), null, null, emptyTopics);
        }

        // Per-topic counts from non-EVENT messages
        Map<String, Long>              topicCounts       = new java.util.LinkedHashMap<>();
        Map<String, java.time.Instant> topicLastActivity = new java.util.LinkedHashMap<>();
        for (Message m : allMessages) {
            String topic = m.topic() != null ? m.topic() : "general";
            topicCounts.merge(topic, 1L, Long::sum);
            if (m.createdAt() != null) {
                topicLastActivity.merge(topic, m.createdAt(),
                                        (a, b) -> a.isAfter(b) ? a : b);
            }
        }

        List<TopicDigest> topicBreakdown = topicCounts.entrySet().stream()
                                                      .map(e -> {
                                                          String                                     name    = e.getKey();
                                                          io.casehub.qhorus.api.message.TopicSummary summary = topicSummaries.get(name);
                                                          java.time.Instant                          lastAct = topicLastActivity.get(name);
                                                          return new TopicDigest(name, e.getValue(),
                                                                                 lastAct != null ? lastAct.toString() : null,
                                                                                 summary != null && summary.resolved(),
                                                                                 summary != null && summary.resolvedAt() != null
                                                                                 ? summary.resolvedAt().toString() : null);
                                                      })
                                                      .toList();

        // Sender and type breakdowns
        Map<String, Integer>  senderBreakdown = new java.util.LinkedHashMap<>();
        Map<String, Integer>  typeBreakdown   = new java.util.LinkedHashMap<>();
        java.util.Set<String> artefactUuids   = new java.util.LinkedHashSet<>();

        for (Message m : allMessages) {
            senderBreakdown.merge(m.sender(), 1, Integer::sum);
            typeBreakdown.merge(m.messageType().name(), 1, Integer::sum);
            if (m.artefactRefs() != null && !m.artefactRefs().isEmpty()) {
                m.artefactRefs().forEach(ref -> artefactUuids.add(ref.uri()));
            }
        }

        java.time.Instant cutoff = java.time.Instant.now().minusSeconds(300);
        List<String> activeAgents = allMessages.stream()
                                               .filter(m -> m.createdAt() != null && m.createdAt().isAfter(cutoff))
                                               .map(m -> m.sender())
                                               .distinct()
                                               .toList();

        List<MessagePreview> recent = allMessages.stream()
                                                 .skip(Math.max(0, allMessages.size() - pageSize))
                                                 .map(m -> {
                                                     String content = m.content() != null ? m.content() : "";
                                                     String preview = content.length() > 120
                                                                      ? content.substring(0, 120) + "…"
                                                                      : content;
                                                     return new MessagePreview(m.id(), m.sender(), m.messageType().name(),
                                                                               preview, m.createdAt() != null ? m.createdAt().toString() : null);
                                                 })
                                                 .toList();

        String oldest = allMessages.get(0).createdAt() != null
                        ? allMessages.get(0).createdAt().toString()
                        : null;
        String newest = allMessages.get(allMessages.size() - 1).createdAt() != null
                        ? allMessages.get(allMessages.size() - 1).createdAt().toString()
                        : null;

        return new ChannelDigest(ch.name(), ch.semantic().name(), ch.paused(),
                                 allMessages.size(), senderBreakdown, typeBreakdown,
                                 artefactUuids.size(), activeAgents, recent, oldest, newest, topicBreakdown);}

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
            String channel,
            String typeFilter,
            String agentId,
            String since,
            Long afterId,
            String correlationId,
            String sort,
            Integer limit) {

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
            String channel,
            String correlationId) {

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
            String channel,
            String ledgerEntryId) {

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
            String correlationId,
            Integer limit) {

        final int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 500) : 100;
        return causalGraphService.buildGraph(correlationId, effectiveLimit, currentPrincipal.tenancyId());
    }

    @Transactional
    public String renderCausalGraph(
            String correlationId,
            Integer limit) {
        final int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 500) : 200;
        var       graph          = causalGraphService.buildGraph(correlationId, effectiveLimit, currentPrincipal.tenancyId());
        return io.casehub.qhorus.runtime.ledger.CausalGraphRenderer.render(graph);
    }


    @Transactional
    public List<StalledObligation> listStalledObligations(
            String channel,
            Integer olderThanSeconds) {

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
            String channel) {

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
            String channel,
            String since) {

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
            String channel,
            Long afterId,
            Integer limit) {

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
            String correlationId,
            Boolean includeContentSearch,
            Integer limit) {

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
            String conditionType,
            String targetName,
            Integer thresholdSeconds,
            Integer thresholdCount,
            Integer similarityPct,
            String notificationChannel,
            String createdBy,
            String action) {
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
            String watchdogId) {
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

    @Transactional
    public Reaction react(
            Long messageId,
            String emoji,
            String actorId) {
        String actor = actorId != null ? actorId : currentPrincipal.actorId();
        return reactionService.react(messageId, emoji, actor, currentPrincipal.tenancyId());
    }

    @Transactional
    public ReactionResult unreact(
            Long messageId,
            String emoji,
            String actorId) {
        String actor = actorId != null ? actorId : currentPrincipal.actorId();
        boolean removed = reactionService.unreact(messageId, emoji, actor);
        return new ReactionResult(messageId, emoji, removed);
    }

    public List<ReactionGroup> getReactions(
            Long messageId) {
        return reactionService.getReactions(messageId);
    }

    public Map<Long, List<ReactionGroup>> getReactionsBatch(
            List<Long> messageIds) {
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


    public ChannelSummaryResult get_channel_summary(String channel) {
        Channel ch = resolveChannel(channel);
        return channelSummaryService.getSummary(ch.id())
                                    .map(s -> new ChannelSummaryResult(ch.name(), s.content(), s.annotations(),
                                                                       s.updatedAt() != null ? s.updatedAt().toString() : null,
                                                                       s.updatedBy(), s.updateAfterMessages(), s.updateAfterSeconds()))
                                    .orElse(new ChannelSummaryResult(ch.name(), null, Map.of(), null, null, null, null));
    }

    public ChannelSummaryResult update_channel_summary(String channel, String summary) {
        Channel ch = resolveChannel(channel);
        var     s  = channelSummaryService.setSummary(ch.id(), summary, currentPrincipal.actorId());
        return new ChannelSummaryResult(ch.name(), s.content(), s.annotations(),
                                        s.updatedAt() != null ? s.updatedAt().toString() : null,
                                        s.updatedBy(), s.updateAfterMessages(), s.updateAfterSeconds());
    }

    public ChannelSummaryResult configure_channel_summary(String channel,
                                                          Integer update_after_messages, Integer update_after_seconds) {
        Channel ch = resolveChannel(channel);
        var     s  = channelSummaryService.configureSummary(ch.id(), update_after_messages, update_after_seconds);
        return new ChannelSummaryResult(ch.name(), s.content(), s.annotations(),
                                        s.updatedAt() != null ? s.updatedAt().toString() : null,
                                        s.updatedBy(), s.updateAfterMessages(), s.updateAfterSeconds());
    }

    public ChannelSummaryResult trigger_channel_summary_update(String channel) {
        Channel ch = resolveChannel(channel);
        return channelSummaryService.triggerUpdate(ch.id())
                                    .map(s -> new ChannelSummaryResult(ch.name(), s.content(), s.annotations(),
                                                                       s.updatedAt() != null ? s.updatedAt().toString() : null,
                                                                       s.updatedBy(), s.updateAfterMessages(), s.updateAfterSeconds()))
                                    .orElseThrow(() -> new IllegalArgumentException(
                                            "No summary configured for channel '" + ch.name() + "'. Use configure_channel_summary first."));
    }

    public List<String> listProjections() {
        return projectionRegistry.registeredNames().stream().sorted().toList();
    }

    public String projectChannel(
            String channel,
            String projectionName,
            Integer maxMessages,
            String topic) {
        return projectAndRender(resolveChannel(channel).id(), projectionRegistry.get(projectionName), maxMessages, topic);
    }

    String getActorCapacity(
            String actorId) {
        if (!capacityView.isResolvable()) {return "Capacity view not available";}
        var capacity = capacityView.get().getCapacity(actorId);
        try {
            return mapper.writeValueAsString(java.util.Map.of(
                    "actorId", capacity.actorId(),
                    "aggregatePressure", capacity.aggregatePressure(),
                    "pressureBySignalType", capacity.pressureBySignalType(),
                    "observedAt", capacity.observedAt().toString()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize capacity", e);
        }
    }

    String listOverloadedActors(
            Double threshold) {
        if (!capacityView.isResolvable()) {return "Capacity view not available";}
        double t          = threshold != null ? threshold : 0.7;
        var    overloaded = capacityView.get().getOverloaded(t);
        var items = overloaded.stream()
                              .map(c -> java.util.Map.of(
                                      "actorId", (Object) c.actorId(),
                                      "aggregatePressure", (Object) c.aggregatePressure(),
                                      "pressureBySignalType", (Object) c.pressureBySignalType()))
                              .toList();
        try {
            return mapper.writeValueAsString(java.util.Map.of("overloaded_actors", items));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize overloaded actors", e);
        }
    }

    String getRedistributionHistory(
            String actorId,
            String channel,
            Integer limit) {
        int maxEntries = limit != null ? Math.min(limit, 100) : 20;
        var ch = resolveChannel(channel);
        String tenancyId = currentPrincipal.tenancyId();
        var entries = ledgerRepo.findByActorIdInChannel(ch.id(), "system:redistribution", maxEntries, tenancyId);
        var filtered = entries.stream()
                              .filter(e -> actorId == null || actorId.equals(e.routingOriginalTarget))
                              .map(this::toLedgerEntryMap)
                              .toList();
        try {
            return mapper.writeValueAsString(java.util.Map.of("redistribution_history", filtered));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize redistribution history", e);
        }
    }

    String setChannelRedistributionThreshold(
            String channel,
            Double threshold) {
        if (threshold != null && (threshold < 0.0 || threshold > 1.0)) {
            throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0, got: " + threshold);
        }
        var ch = resolveChannel(channel);
        channelService.setRedistributionCapacityThreshold(ch.id(), threshold);
        return "Redistribution threshold " + (threshold != null ? "set to " + threshold : "cleared")
               + " for channel " + ch.name();
    }

    String getChannelRedistributionThreshold(
            String channel) {
        var    ch         = resolveChannel(channel);
        Double configured = ch.redistributionCapacityThreshold();
        double effective  = configured != null ? configured : globalRedistributeThreshold;
        try {
            return mapper.writeValueAsString(java.util.Map.of(
                    "channel", ch.name(),
                    "configured", configured != null ? configured.toString() : "null",
                    "effective", effective));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize threshold", e);
        }
    }

    String setChannelRoutingCapacityThreshold(
            String channel,
            Double threshold) {
        if (threshold != null && (threshold < 0.0 || threshold > 1.0)) {
            throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0, got: " + threshold);
        }
        var ch = resolveChannel(channel);
        channelService.setRoutingCapacityThreshold(ch.id(), threshold);
        return "Routing capacity threshold " + (threshold != null ? "set to " + threshold : "cleared")
               + " for channel " + ch.name();
    }

    String getChannelRoutingCapacityThreshold(
            String channel) {
        var    ch         = resolveChannel(channel);
        Double configured = ch.routingCapacityThreshold();
        double effective  = configured != null ? configured : qhorusConfig.routing().defaultCapacityThreshold().orElse(0.8);
        try {
            return mapper.writeValueAsString(java.util.Map.of(
                    "channel", ch.name(),
                    "configured", configured != null ? configured.toString() : "null",
                    "effective", effective));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize threshold", e);
        }
    }


}
