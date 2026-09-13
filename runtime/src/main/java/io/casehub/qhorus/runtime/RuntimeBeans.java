package io.casehub.qhorus.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.capacity.ActorCapacityView;
import io.casehub.platform.api.capacity.CapacityPressureEvent;
import io.casehub.platform.api.capacity.RedistributionPolicy;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.ChannelSummaryUpdatedEvent;
import io.casehub.qhorus.api.channel.PresenceChangedEvent;
import io.casehub.qhorus.api.capacity.RedistributionExecutedEvent;
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
import io.casehub.qhorus.api.spi.CommitmentAttestationPolicy;
import io.casehub.qhorus.api.spi.ObligorTrustPolicy;
import io.casehub.qhorus.api.spi.PeerReviewRequestedEvent;
import io.casehub.qhorus.api.spi.RenderableProjection;
import io.casehub.qhorus.api.spi.SummaryUpdateHook;
import io.casehub.qhorus.api.store.*;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.api.watchdog.WatchdogAlertEvent;
import io.casehub.qhorus.runtime.capacity.QhorusRedistributionExecutor;
import io.casehub.qhorus.runtime.capacity.RedistributionDelegate;
import io.casehub.qhorus.runtime.channel.*;
import io.casehub.qhorus.runtime.config.DeliveryConfig;
import io.casehub.qhorus.runtime.config.PresenceConfig;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.runtime.config.QhorusTracingConfig;
import io.casehub.qhorus.runtime.gateway.*;
import io.casehub.qhorus.runtime.identity.InboundTenancyContext;
import io.casehub.qhorus.runtime.instance.InstanceService;
import io.casehub.qhorus.runtime.ledger.LedgerWriteService;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import io.casehub.qhorus.runtime.ledger.ReviewerResolver;
import io.casehub.qhorus.runtime.message.*;
import io.casehub.qhorus.runtime.message.protocol.ProtocolRegistry;
import io.casehub.qhorus.runtime.watchdog.WatchdogEvaluationService;
import io.cloudevents.CloudEvent;


import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.quarkus.arc.DefaultBean;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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

    @Produces @ApplicationScoped
    public SpaceService spaceService(SpaceStore spaceStore, ChannelStore channelStore,
                                     CurrentPrincipal currentPrincipal,
                                     Event<ChannelMutationEvent> mutationEvent) {
        return new SpaceService(spaceStore, channelStore, currentPrincipal, mutationEvent::fire);
    }

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

    @Produces @ApplicationScoped
    public DeliveryBatchExecutor deliveryBatchExecutor(CrossTenantMessageStore messageStore,
                                                       CrossTenantChannelStore channelStore,
                                                       DeliveryCursorStore cursorStore,
                                                       DeliveryConfig config,
                                                       ChannelMembershipStore channelMembershipStore,
                                                       Instance<Tracer> tracerInstance,
                                                       QhorusTracingConfig tracingConfig) {
        return new DeliveryBatchExecutor(messageStore, channelStore, cursorStore, config,
                channelMembershipStore,
                tracerInstance.isResolvable() ? tracerInstance::get : null, tracingConfig);
    }

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

    @Produces @ApplicationScoped
    public CommitmentService commitmentService(CommitmentStore store,
                                               Event<CommitmentDeclinedEvent> declinedEvents,
                                               Event<CommitmentExpiredEvent> expiredEvents,
                                               Instance<Tracer> tracerInstance,
                                               QhorusTracingConfig tracingConfig) {
        return new CommitmentService(store, declinedEvents::fire, e -> {
            try { expiredEvents.fire(e); } catch (Exception ex) { /* logged by core */ }
        }, tracerInstance.isResolvable() ? tracerInstance::get : null, tracingConfig);
    }

    @Produces @ApplicationScoped
    public EnforcementExecutor enforcementExecutor(MessageDispatcher messageDispatcher,
                                                    ChannelService channelService,
                                                    CommitmentService commitmentService,
                                                    Event<EnforcementBlockedEvent> enforcementBlockedEvent,
                                                    ObjectMapper objectMapper) {
        return new EnforcementExecutor(messageDispatcher, channelService, commitmentService,
                e -> enforcementBlockedEvent.fireAsync(e), objectMapper);
    }

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

    @Produces @ApplicationScoped
    public MessageService messageService(ChannelService channelService,
                                          CrossTenantChannelStore crossTenantChannelStore,
                                          CurrentPrincipal currentPrincipal,
                                          MessageStore messageStore,
                                          CommitmentService commitmentService,
                                          MessageTypePolicy messageTypePolicy,
                                          RateLimiter rateLimiter,
                                          QhorusConfig config,
                                          ObligorTrustPolicy obligorTrustPolicy,
                                          TransactionSynchronizationRegistry tsr,
                                          InstanceService instanceService,
                                          DeliverySignalQueue deliverySignalQueue,
                                          TopicService topicService,
                                          CorrelationIntegrityChecker correlationIntegrityChecker,
                                          ProtocolRegistry protocolRegistry,
                                          CommitmentStore commitmentStore,
                                          ChannelActivityBroadcaster broadcaster,
                                          Instance<Tracer> tracerInstance,
                                          QhorusTracingConfig tracingConfig,
                                          EnforcementExecutor enforcementExecutor,
                                          RoutingBridge routingBridge,
                                          @Any Instance<io.casehub.qhorus.api.gateway.MessageObserver> observers,
                                          LedgerWriteService ledgerWriteService) {
        return new MessageService(channelService, crossTenantChannelStore, currentPrincipal,
                messageStore, commitmentService, messageTypePolicy, rateLimiter, config,
                obligorTrustPolicy, tsr, instanceService, deliverySignalQueue, topicService,
                correlationIntegrityChecker, protocolRegistry, commitmentStore, broadcaster,
                tracerInstance.isResolvable() ? tracerInstance::get : null, tracingConfig,
                enforcementExecutor, routingBridge,
                (channelName, channelId, tenancyId, message) ->
                        io.casehub.qhorus.runtime.message.MessageObserverDispatcher.dispatch(
                                channelName, channelId, tenancyId, message, observers.handles(), tsr),
                (channelName, channelId, tenancyId, message) ->
                        io.casehub.qhorus.runtime.message.MessageObserverDispatcher.dispatchClusterOnly(
                                channelName, channelId, tenancyId, message, observers.handles()),
                (dispatch, messageId, commitmentId, occurredAt, routingOutcome) ->
                        ledgerWriteService.record(dispatch, messageId, commitmentId, occurredAt, routingOutcome));
    }

    // ── Capacity ───────────────────────────────────────────────────────────

    @Produces @ApplicationScoped
    public RedistributionDelegate redistributionDelegate(ChannelSummaryService summaryService,
                                                          MessageService messageService,
                                                          RoutingBridge routingBridge,
                                                          CrossTenantChannelStore channelStore,
                                                          MessageStore messageStore,
                                                          InboundTenancyContext inboundTenancyContext,
                                                          Event<RedistributionExecutedEvent> executedEvents,
                                                          @org.eclipse.microprofile.config.inject.ConfigProperty(
                                                                  name = "casehub.capacity.redistribution.redistribute-threshold",
                                                                  defaultValue = "0.85") double threshold) {
        return new RedistributionDelegate(summaryService, messageService, routingBridge,
                channelStore, messageStore, inboundTenancyContext::set,
                e -> executedEvents.fireAsync(e), threshold);
    }

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

    @Produces @ApplicationScoped
    public WatchdogEvaluationService watchdogEvaluationService(QhorusConfig config,
                                                                MessageService messageService,
                                                                WatchdogStore watchdogStore,
                                                                CrossTenantChannelStore crossTenantChannelStore,
                                                                CrossTenantMessageStore crossTenantMessageStore,
                                                                CrossTenantCommitmentStore crossTenantCommitmentStore,
                                                                CrossTenantWatchdogStore crossTenantWatchdogStore,
                                                                InstanceStore instanceStore,
                                                                Event<WatchdogAlertEvent> alertEvents,
                                                                MessageLedgerEntryRepository messageRepo,
                                                                ChannelMembershipStore channelMembershipStore,
                                                                ChannelService channelService,
                                                                InstanceService instanceService,
                                                                CommitmentService commitmentService,
                                                                ObjectMapper objectMapper) {
        return new WatchdogEvaluationService(config, messageService, watchdogStore,
                crossTenantChannelStore, crossTenantMessageStore, crossTenantCommitmentStore,
                crossTenantWatchdogStore, instanceStore, e -> alertEvents.fireAsync(e),
                (channelId, tenancyId) -> messageRepo.findLatestContextPressure(channelId, tenancyId)
                        .stream()
                        .map(entry -> new WatchdogEvaluationService.ContextPressureEntry(
                                entry.actorId, entry.contextWindowPct))
                        .toList(),
                channelMembershipStore, channelService, instanceService, commitmentService, objectMapper);
    }

    // ── CDI event observers ────────────────────────────────────────────────

    void onCapacityPressure(@ObservesAsync CapacityPressureEvent event,
                            QhorusRedistributionExecutor executor) {
        executor.onCapacityPressure(event);
    }

    void onMessageReceived(@ObservesAsync MessageReceivedEvent event,
                           QhorusCloudEventAdapter adapter) {
        adapter.onMessageReceived(event);
    }

    void onStartup(@Observes StartupEvent ev, ChannelGateway gateway) {
        gateway.initAllChannels();
    }

    // ── Scheduled tasks ────────────────────────────────────────────────────

    @Inject ChannelSummaryScheduler summarySchedulerBean;
    @Inject DeliveryService deliveryServiceBean;

    @PostConstruct
    void startDelivery() {
        deliveryServiceBean.start();
    }

    @PreDestroy
    void stopDelivery() {
        deliveryServiceBean.stop();
    }

    @Scheduled(every = "${casehub.qhorus.summary.check-interval-seconds:60}s",
               identity = "summary-update-check")
    void summarySweep() {
        summarySchedulerBean.sweep();
    }

    @Scheduled(every = "${casehub.qhorus.delivery.reconciliation-interval:30s}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void deliveryReconcile() {
        deliveryServiceBean.reconcileAll();
    }
}
