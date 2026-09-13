package io.casehub.qhorus.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.capacity.ActorCapacityView;
import io.casehub.platform.api.capacity.CapacityPressureEvent;
import io.casehub.platform.api.capacity.RedistributionPolicy;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.capacity.RedistributionExecutedEvent;
import io.casehub.qhorus.api.channel.ChannelSummaryUpdatedEvent;
import io.casehub.qhorus.api.channel.PresenceChangedEvent;
import io.casehub.qhorus.api.event.ChannelMutationEvent;
import io.casehub.qhorus.api.gateway.AgentChannelBackend;
import io.casehub.qhorus.api.gateway.ChannelActivityBroadcaster;
import io.casehub.qhorus.api.gateway.ChannelClosedEvent;
import io.casehub.qhorus.api.gateway.ChannelInitialisedEvent;
import io.casehub.qhorus.api.gateway.InboundNormaliser;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.CommitmentDeclinedEvent;
import io.casehub.qhorus.api.message.CommitmentExpiredEvent;
import io.casehub.qhorus.api.message.EnforcementBlockedEvent;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.ReactionChangedEvent;
import io.casehub.qhorus.api.spi.ChannelProtocol;
import io.casehub.qhorus.api.spi.ObligorTrustPolicy;
import io.casehub.qhorus.api.spi.PeerReviewRequestedEvent;
import io.casehub.qhorus.api.spi.RenderableProjection;
import io.casehub.qhorus.api.spi.SummaryUpdateHook;
import io.casehub.qhorus.api.store.ChannelBindingStore;
import io.casehub.qhorus.api.store.ChannelMembershipStore;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.store.ChannelSummaryStore;
import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;
import io.casehub.qhorus.api.store.CrossTenantChannelSummaryStore;
import io.casehub.qhorus.api.store.CrossTenantCommitmentStore;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import io.casehub.qhorus.api.store.CrossTenantWatchdogStore;
import io.casehub.qhorus.api.store.DataStore;
import io.casehub.qhorus.api.store.DeliveryCursorStore;
import io.casehub.qhorus.api.store.InstanceStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.ReactionStore;
import io.casehub.qhorus.api.store.SpaceStore;
import io.casehub.qhorus.api.store.TopicStore;
import io.casehub.qhorus.api.store.WatchdogStore;
import io.casehub.qhorus.api.watchdog.WatchdogAlertEvent;
import io.casehub.qhorus.runtime.capacity.QhorusRedistributionExecutor;
import io.casehub.qhorus.runtime.capacity.RedistributionDelegate;
import io.casehub.qhorus.runtime.config.DeliveryConfig;
import io.casehub.qhorus.runtime.config.PresenceConfig;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.runtime.config.QhorusTracingConfig;
import io.casehub.qhorus.runtime.channel.*;
import io.casehub.qhorus.runtime.gateway.*;
import io.casehub.qhorus.runtime.identity.InboundTenancyContext;
import io.casehub.qhorus.runtime.instance.InstanceService;
import io.casehub.qhorus.runtime.message.*;
import io.casehub.qhorus.runtime.ledger.AgreementCredibilityPolicy;
import io.casehub.qhorus.runtime.ledger.LedgerWriteService;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import io.casehub.qhorus.runtime.ledger.ReviewerResolver;
import io.casehub.qhorus.runtime.message.protocol.ProtocolRegistry;
import io.casehub.qhorus.runtime.watchdog.WatchdogEvaluationService;
import io.cloudevents.CloudEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.quarkus.arc.DefaultBean;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class RuntimeBeans {

    // ── Channel ────────────────────────────────────────────────────────────

    // SpaceService → CdiSpaceService (runtime/cdi/)

    @Produces @ApplicationScoped
    public ChannelSummaryService channelSummaryService(ChannelSummaryStore summaryStore,
                                                        ChannelService channelService,
                                                        CrossTenantChannelStore crossTenantChannelStore,
                                                        MessageStore messageStore,
                                                        SummaryUpdateHook hook,
                                                        Event<ChannelSummaryUpdatedEvent> summaryEvents) {
        return new ChannelSummaryService(summaryStore, channelService, crossTenantChannelStore,
                messageStore, hook, e -> summaryEvents.fireAsync(e));
    }

    @Produces @ApplicationScoped
    public ChannelSummaryScheduler channelSummaryScheduler(QhorusConfig config,
                                                            CrossTenantChannelSummaryStore crossTenantSummaryStore,
                                                            ChannelSummaryStore summaryStore,
                                                            CrossTenantChannelStore crossTenantChannelStore,
                                                            CrossTenantMessageStore crossTenantMessageStore,
                                                            SummaryUpdateHook hook,
                                                            Event<ChannelSummaryUpdatedEvent> summaryEvents) {
        return new ChannelSummaryScheduler(config, crossTenantSummaryStore, summaryStore,
                crossTenantChannelStore, crossTenantMessageStore, hook, e -> summaryEvents.fireAsync(e));
    }

    @Produces @ApplicationScoped
    public PresenceService presenceService(PresenceConfig config, Clock clock,
                                           ChannelMembershipService membershipService,
                                           CurrentPrincipal currentPrincipal,
                                           Event<PresenceChangedEvent> presenceEvent) {
        return new PresenceService(config, clock, membershipService, currentPrincipal,
                e -> presenceEvent.fireAsync(e));
    }

    @Produces @ApplicationScoped
    public ChannelCreateHelper channelCreateHelper(ChannelStore channelStore,
                                                    ChannelBindingStore channelBindingStore,
                                                    ChannelGateway channelGateway,
                                                    CurrentPrincipal currentPrincipal) {
        return new ChannelCreateHelper(channelStore, channelBindingStore, channelGateway, currentPrincipal);
    }

    @Produces @ApplicationScoped
    public ChannelService channelService(CurrentPrincipal currentPrincipal,
                                         ChannelStore channelStore,
                                         MessageStore messageStore,
                                         ChannelMembershipStore membershipStore,
                                         ChannelBindingStore channelBindingStore,
                                         ChannelGateway channelGateway,
                                         ChannelCreateHelper channelCreateHelper) {
        return new ChannelService(currentPrincipal, channelStore, messageStore, membershipStore,
                channelBindingStore, channelGateway, channelCreateHelper);
    }

    // ── Gateway ────────────────────────────────────────────────────────────

    @Produces @DefaultBean @ApplicationScoped
    public InProcessMessageBus inProcessMessageBus(Event<MessageReceivedEvent> cdiEvent) {
        return new InProcessMessageBus(e -> cdiEvent.fireAsync(e));
    }

    @Produces @ApplicationScoped
    public QhorusCloudEventAdapter qhorusCloudEventAdapter(Event<CloudEvent> cloudEventBus,
                                                            ObjectMapper objectMapper) {
        return new QhorusCloudEventAdapter(e -> cloudEventBus.fireAsync(e), objectMapper);
    }

    @Produces @ApplicationScoped
    public ChannelGateway channelGateway(AgentChannelBackend agentBackend,
                                         InboundNormaliser normaliser,
                                         MessageService messageService,
                                         ChannelService channelService,
                                         CrossTenantChannelStore crossTenantChannelStore,
                                         Event<ChannelInitialisedEvent> channelInitialisedEvents,
                                         Event<ChannelClosedEvent> channelClosedEvents,
                                         DeliveryConfig deliveryConfig,
                                         CrossTenantMessageStore crossTenantMessageStore,
                                         ChannelMembershipService membershipService,
                                         Instance<Tracer> tracerInstance,
                                         QhorusTracingConfig tracingConfig) {
        ChannelGateway gw = new ChannelGateway(agentBackend, normaliser, messageService, channelService,
                crossTenantChannelStore, channelInitialisedEvents::fire, channelClosedEvents::fire,
                deliveryConfig, crossTenantMessageStore, membershipService,
                tracerInstance.isResolvable() ? tracerInstance::get : null, tracingConfig);
        messageService.setChannelGateway(gw);
        return gw;
    }

    // DeliveryBatchExecutor → CdiDeliveryBatchExecutor (runtime/cdi/)

    @Produces @ApplicationScoped
    public DeliveryService deliveryService(DeliverySignalQueue signalQueue,
                                           DeliveryConfig config,
                                           ChannelGateway gateway,
                                           ManagedExecutor managedExecutor,
                                           DeliveryBatchExecutor batchExecutor,
                                           DeliveryCursorStore cursorStore,
                                           CrossTenantMessageStore messageStore,
                                           CrossTenantChannelStore channelStore,
                                           ChannelMembershipStore channelMembershipStore,
                                           Instance<MeterRegistry> meterRegistryInstance) {
        return new DeliveryService(signalQueue, config, gateway, managedExecutor,
                batchExecutor, cursorStore, messageStore, channelStore, channelMembershipStore,
                meterRegistryInstance.isResolvable() ? meterRegistryInstance.get() : null);
    }

    // ── Message ────────────────────────────────────────────────────────────

    // CommitmentService → CdiCommitmentService (runtime/cdi/)
    // EnforcementExecutor → CdiEnforcementExecutor (runtime/cdi/)

    @Produces @ApplicationScoped
    public ReactionService reactionService(ReactionStore reactionStore,
                                           Event<ReactionChangedEvent> reactionEvent,
                                           CurrentPrincipal currentPrincipal) {
        return new ReactionService(reactionStore, e -> reactionEvent.fireAsync(e), currentPrincipal);
    }

    @Produces @ApplicationScoped
    public RoutingBridge routingBridge(Instance<io.casehub.eidos.api.AgentRegistry> agentRegistryInstance,
                                      Instance<io.casehub.eidos.api.AgentSelector> agentSelectorInstance,
                                      Instance<io.casehub.ledger.runtime.service.TrustGateService> trustGateServiceInstance,
                                      Instance<ActorCapacityView> capacityViewInstance,
                                      QhorusConfig config) {
        return new RoutingBridge(
                agentRegistryInstance.isResolvable() ? agentRegistryInstance.get() : null,
                agentSelectorInstance.isResolvable() ? agentSelectorInstance.get() : null,
                trustGateServiceInstance.isResolvable() ? trustGateServiceInstance.get() : null,
                capacityViewInstance.isResolvable() ? capacityViewInstance.get() : null,
                config);
    }

    @Produces @ApplicationScoped
    public ProjectionRegistry projectionRegistry(@Any Instance<RenderableProjection<?>> bundles) {
        List<RenderableProjection<?>> list = StreamSupport.stream(bundles.spliterator(), false).toList();
        return new ProjectionRegistry(list);
    }

    @Produces @ApplicationScoped
    public ProtocolRegistry protocolRegistry(@Any Instance<ChannelProtocol> protocols) {
        List<ChannelProtocol> list = StreamSupport.stream(protocols.spliterator(), false).toList();
        return new ProtocolRegistry(list);
    }

    // MessageService → CdiMessageService (runtime/cdi/)

    // ── Capacity ───────────────────────────────────────────────────────────

    // RedistributionDelegate → CdiRedistributionDelegate (runtime/cdi/)

    @Produces @ApplicationScoped
    public QhorusRedistributionExecutor redistributionExecutor(RedistributionDelegate delegate,
                                                                RedistributionPolicy policy,
                                                                CrossTenantCommitmentStore commitmentStore,
                                                                MessageLedgerEntryRepository messageRepo) {
        return new QhorusRedistributionExecutor(delegate, policy, commitmentStore,
                actorId -> messageRepo.findLatestEntryByActor(actorId)
                        .map(e -> Duration.between(e.occurredAt, Instant.now()))
                        .orElse(Duration.ofDays(365)));
    }

    // ── Ledger ─────────────────────────────────────────────────────────────

    @Produces @ApplicationScoped
    public ReviewerResolver reviewerResolver(ChannelStore channelStore,
                                             InstanceService instanceService,
                                             Event<PeerReviewRequestedEvent> reviewRequestedEvent) {
        return new ReviewerResolver(channelStore, instanceService,
                e -> reviewRequestedEvent.fireAsync(e));
    }

    // ── Watchdog ───────────────────────────────────────────────────────────

    // WatchdogEvaluationService → CdiWatchdogEvaluationService (runtime/cdi/)

    // ── CDI event observers ────────────────────────────────────────────────


// ── Strip classes — simple constructor forwarding ───────────────

    @Produces @ApplicationScoped
    public InstanceService instanceService(InstanceStore instanceStore) {
        return new InstanceService(instanceStore);
    }

    // DataService → CdiDataService (runtime/cdi/)

    @Produces @ApplicationScoped
    public TopicService topicService(TopicStore topicStore, MessageStore messageStore,
                                     CommitmentStore commitmentStore, CurrentPrincipal currentPrincipal) {
        return new TopicService(topicStore, messageStore, commitmentStore, currentPrincipal);
    }

    @Produces @ApplicationScoped
    public CorrelationIntegrityChecker correlationIntegrityChecker(CommitmentStore commitmentStore,
                                                                   MessageStore messageStore) {
        return new CorrelationIntegrityChecker(commitmentStore, messageStore);
    }

    @Produces @ApplicationScoped
    public ChannelMembershipService channelMembershipService(ChannelMembershipStore membershipStore,
                                                             MessageStore messageStore,
                                                             CurrentPrincipal currentPrincipal) {
        return new ChannelMembershipService(membershipStore, messageStore, currentPrincipal);
    }

    @Produces @ApplicationScoped
    public RateLimiter rateLimiter() {
        return new RateLimiter();
    }

    @Produces @ApplicationScoped
    public DeliverySignalQueue deliverySignalQueue() {
        return new DeliverySignalQueue();
    }

    @Produces @ApplicationScoped
    public ProjectionService projectionService(MessageStore messageStore,
                                               QhorusEntityMapper mapper) {
        return new ProjectionService(messageStore, mapper::toMessageView);
    }

    @Produces @ApplicationScoped
    public io.casehub.qhorus.runtime.audit.EvidentialChecker evidentialChecker(DataStore dataStore,
                                                                               MessageStore messageStore,
                                                                               CommitmentStore commitmentStore) {
        return new io.casehub.qhorus.runtime.audit.EvidentialChecker(dataStore, messageStore, commitmentStore);
    }

// ── Stripped classes — @DefaultBean overridable defaults ───────────

    @Produces
    @DefaultBean
    @ApplicationScoped
    public io.casehub.qhorus.api.spi.SummaryUpdateHook summaryUpdateHook() {
        return new io.casehub.qhorus.runtime.channel.NoOpSummaryUpdateHook();
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public io.casehub.qhorus.api.spi.InstanceActorIdProvider instanceActorIdProvider() {
        return new io.casehub.qhorus.runtime.ledger.DefaultInstanceActorIdProvider();
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public io.casehub.qhorus.api.gateway.ChannelActivityBroadcaster channelActivityBroadcaster() {
        return new io.casehub.qhorus.runtime.gateway.NoOpChannelActivityBroadcaster();
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public io.casehub.qhorus.api.gateway.AgentChannelBackend agentChannelBackend() {
        return new QhorusChannelBackend();
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public MessageTypePolicy messageTypePolicy() {
        return new StoredMessageTypePolicy();
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public io.casehub.qhorus.api.spi.ObligorTrustPolicy obligorTrustPolicy(
            QhorusConfig config,
            Instance<io.casehub.ledger.runtime.service.TrustGateService> trustGateServiceInstance) {
        return new DefaultObligorTrustPolicy(
                config.commitment().minObligorTrust(),
                trustGateServiceInstance.isResolvable() ? trustGateServiceInstance.get() : null);
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public io.casehub.qhorus.api.spi.CommitmentAttestationPolicy commitmentAttestationPolicy(
            QhorusConfig config,
            io.casehub.qhorus.runtime.audit.EvidentialChecker evidentialChecker) {
        return new io.casehub.qhorus.runtime.ledger.StoredCommitmentAttestationPolicy(
                config.attestation().doneConfidence(),
                config.attestation().failureConfidence(),
                config.attestation().declineConfidence(),
                config.attestation().responseConfidence(),
                evidentialChecker);
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public AgreementCredibilityPolicy agreementCredibilityPolicy(
            io.casehub.ledger.api.spi.LedgerEntryRepository ledger,
            QhorusConfig config) {
        return new AgreementCredibilityPolicy(ledger,
                                              config.attestation().credibilityMinDataPoints(),
                                              config.attestation().credibilityLowAgreementThreshold());
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public io.casehub.qhorus.api.watchdog.WatchdogAlertRouter watchdogAlertRouter(QhorusConfig config) {
        List<io.casehub.qhorus.api.watchdog.AlertDeliveryTarget> list =
                config.watchdog().alert().endpoints().stream()
                        .map(ep -> new io.casehub.qhorus.api.watchdog.AlertDeliveryTarget(ep.connectorId(), ep.destination()))
                        .toList();
        return new io.casehub.qhorus.runtime.watchdog.ConfiguredWatchdogAlertRouter(list);
    }


// ── Protocol implementations ──────────────────────────────────────

    @Produces
    @ApplicationScoped
    public io.casehub.qhorus.runtime.message.protocol.RoundRobinProtocol roundRobinProtocol() {
        return new io.casehub.qhorus.runtime.message.protocol.RoundRobinProtocol();
    }

    @Produces
    @ApplicationScoped
    public io.casehub.qhorus.runtime.message.protocol.ContributionRequiredProtocol contributionRequiredProtocol(QhorusConfig config) {
        return new io.casehub.qhorus.runtime.message.protocol.ContributionRequiredProtocol(
                config.protocol().contributionRequired().maxConsecutive());
    }

    @Produces
    @ApplicationScoped
    public io.casehub.qhorus.runtime.message.protocol.RequestResponseProtocol requestResponseProtocol(QhorusConfig config) {
        return new io.casehub.qhorus.runtime.message.protocol.RequestResponseProtocol(
                config.protocol().requestResponse().maxOpenQueries());
    }

    @Produces
    @ApplicationScoped
    public io.casehub.qhorus.runtime.message.protocol.TaskCompletionProtocol taskCompletionProtocol(QhorusConfig config) {
        return new io.casehub.qhorus.runtime.message.protocol.TaskCompletionProtocol(
                config.protocol().taskCompletion().maxOpenCommands());
    }

    void onCapacityPressure(@ObservesAsync CapacityPressureEvent event,
                            QhorusRedistributionExecutor executor) {
        executor.onCapacityPressure(event);
    }

    void onMessageReceived(@ObservesAsync MessageReceivedEvent event,
                           QhorusCloudEventAdapter adapter) {
        adapter.onMessageReceived(event);
    }

    void onStartup(@Observes StartupEvent ev, ChannelGateway gateway, DeliveryService deliveryService) {
        gateway.initAllChannels();
        deliveryService.start();
    }

    void onShutdown(@Observes io.quarkus.runtime.ShutdownEvent ev, DeliveryService deliveryService) {
        deliveryService.stop();
    }

    // ── Scheduled tasks ────────────────────────────────────────────────────

    @Inject Instance<ChannelSummaryScheduler> summarySchedulerInstance;
    @Inject Instance<DeliveryService> deliveryServiceInstance;

    @Scheduled(every = "${casehub.qhorus.summary.check-interval-seconds:60}s",
               identity = "summary-update-check")
    void summarySweep() {
        summarySchedulerInstance.get().sweep();
    }

    @Scheduled(every = "${casehub.qhorus.delivery.reconciliation-interval:30s}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void deliveryReconcile() {
        deliveryServiceInstance.get().reconcileAll();
    }
}
