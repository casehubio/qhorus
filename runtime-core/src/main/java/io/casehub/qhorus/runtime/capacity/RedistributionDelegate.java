package io.casehub.qhorus.runtime.capacity;

import io.casehub.qhorus.api.capacity.RedistributionExecutedEvent;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.runtime.channel.ChannelSummaryService;
import io.casehub.qhorus.runtime.message.MessageService;
import io.casehub.qhorus.runtime.message.RoutingBridge;
import io.casehub.platform.api.capacity.RedistributionDecision;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class RedistributionDelegate {

    private static final Logger LOG = Logger.getLogger(RedistributionDelegate.class);

    private final ChannelSummaryService summaryService;
    private final MessageService messageService;
    private final RoutingBridge routingBridge;
    private final CrossTenantChannelStore channelStore;
    private final MessageStore messageStore;
    private final Consumer<String> tenancyContextSetter;
    private final Consumer<RedistributionExecutedEvent> executedConsumer;
    private final double globalRedistributeThreshold;

    public RedistributionDelegate(ChannelSummaryService summaryService,
                                  MessageService messageService,
                                  RoutingBridge routingBridge,
                                  CrossTenantChannelStore channelStore,
                                  MessageStore messageStore,
                                  Consumer<String> tenancyContextSetter,
                                  Consumer<RedistributionExecutedEvent> executedConsumer,
                                  double globalRedistributeThreshold) {
        this.summaryService = summaryService;
        this.messageService = messageService;
        this.routingBridge = routingBridge;
        this.channelStore = channelStore;
        this.messageStore = messageStore;
        this.tenancyContextSetter = tenancyContextSetter;
        this.executedConsumer = executedConsumer;
        this.globalRedistributeThreshold = globalRedistributeThreshold;
    }

    @Transactional
    public void compress(String actorId, List<Commitment> obligations) {
        Set<UUID> channelIds = new HashSet<>();
        for (var c : obligations) {
            channelIds.add(c.channelId());
        }

        int compressedCount = 0;
        for (UUID channelId : channelIds) {
            try {
                var summary = summaryService.getSummary(channelId);
                if (summary.isPresent()
                        && summaryService.countMessagesSince(channelId,
                                summary.get().lastUpdatedMessageId()) > 0) {
                    summaryService.triggerUpdate(channelId);
                    compressedCount++;
                }
            } catch (Exception e) {
                LOG.warnf("Compression failed for channel %s: %s", channelId, e.getMessage());
            }
        }
        executedConsumer.accept(RedistributionExecutedEvent.compressed(actorId, compressedCount));
    }

    @Transactional
    public RedistributionResult redistribute(String actorId, List<Commitment> obligations,
                                              RedistributionDecision.Redistribute decision,
                                              double aggregatePressure) {
        var redistributable = obligations.stream()
                .filter(c -> c.capabilityTag() != null)
                .toList();

        int successCount = 0;
        int filteredCount = 0;
        for (var commitment : redistributable) {
            try {
                tenancyContextSetter.accept(commitment.tenancyId());

                var originalMessages = messageStore.scan(
                        io.casehub.qhorus.api.store.query.MessageQuery.builder()
                                .channelId(commitment.channelId())
                                .correlationId(commitment.correlationId())
                                .limit(1).build());
                if (originalMessages.isEmpty()) {
                    LOG.warnf("No original message for correlation %s — skipping",
                            commitment.correlationId());
                    continue;
                }
                Long inReplyTo = originalMessages.get(0).id();

                var channel = channelStore.findById(commitment.channelId()).orElse(null);
                if (channel == null) continue;

                double threshold = channel.redistributionCapacityThreshold() != null
                        ? channel.redistributionCapacityThreshold()
                        : globalRedistributeThreshold;
                if (aggregatePressure < threshold) {
                    filteredCount++;
                    continue;
                }

                var dispatch = io.casehub.qhorus.api.message.MessageDispatch.builder()
                        .channelId(commitment.channelId())
                        .sender("system:redistribution")
                        .type(io.casehub.qhorus.api.message.MessageType.HANDOFF)
                        .content("Capacity redistribution: pressure " + aggregatePressure)
                        .correlationId(commitment.correlationId())
                        .target("role:" + commitment.capabilityTag())
                        .inReplyTo(inReplyTo)
                        .tenancyId(commitment.tenancyId())
                        .actorType(io.casehub.platform.api.identity.ActorType.SYSTEM)
                        .build();

                RoutingBridge.RoutingOutcome routingOutcome;
                try {
                    routingOutcome = routingBridge.resolve(dispatch, channel, commitment.tenancyId());
                } catch (Exception e) {
                    LOG.warnf("Cannot redistribute %s — no target for capability '%s': %s",
                            commitment.correlationId(), commitment.capabilityTag(), e.getMessage());
                    continue;
                }
                if (routingOutcome == null) continue;

                if (decision.excludeActors().contains(routingOutcome.resolvedTarget())) {
                    LOG.debugf("Excluded actor prevented for %s: %s",
                            commitment.correlationId(), routingOutcome.resolvedTarget());
                    continue;
                }

                dispatch = dispatch.withTarget(routingOutcome.resolvedTarget());
                messageService.dispatch(dispatch);
                successCount++;
            } catch (Exception e) {
                LOG.warnf("Redistribution failed for %s: %s",
                        commitment.correlationId(), e.getMessage());
            }
        }

        int attemptedCount = redistributable.size() - filteredCount;
        executedConsumer.accept(
                RedistributionExecutedEvent.redistributed(actorId, successCount, attemptedCount, filteredCount));
        return new RedistributionResult(successCount, attemptedCount, filteredCount);
    }

    public void escalate(String actorId, String reason) {
        LOG.warnf("Escalation for actor %s: %s", actorId, reason);
        executedConsumer.accept(RedistributionExecutedEvent.escalated(actorId, reason));
    }
}
