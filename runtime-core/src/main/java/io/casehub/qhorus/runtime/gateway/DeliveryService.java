package io.casehub.qhorus.runtime.gateway;

import io.casehub.qhorus.api.gateway.ChannelBackend;
import io.casehub.qhorus.api.gateway.DeliveryCursor;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import io.casehub.qhorus.api.store.DeliveryCursorStore;
import io.casehub.qhorus.runtime.config.DeliveryConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class DeliveryService implements DeliveryBatchExecutor.HealthCallback {

    private static final Logger LOG = Logger.getLogger(DeliveryService.class);

    DeliverySignalQueue signalQueue;
    DeliveryConfig config;
    ChannelGateway gateway;
    MeterRegistry meterRegistry;
    Executor executor;
    DeliveryBatchExecutor batchExecutor;
    DeliveryCursorStore cursorStore;
    CrossTenantMessageStore messageStore;
    io.casehub.qhorus.api.store.CrossTenantChannelStore channelStore;
    io.casehub.qhorus.api.store.ChannelMembershipStore channelMembershipStore;

    private final Set<String> activeDeliveries = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private final Set<String> unhealthy = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Integer> participantFailures = new ConcurrentHashMap<>();

    volatile boolean running;


    DeliveryService() {}

    public DeliveryService(DeliverySignalQueue signalQueue,
                           DeliveryConfig config,
                           ChannelGateway gateway,
                           Executor executor,
                           DeliveryBatchExecutor batchExecutor,
                           DeliveryCursorStore cursorStore,
                           CrossTenantMessageStore messageStore,
                           io.casehub.qhorus.api.store.CrossTenantChannelStore channelStore,
                           io.casehub.qhorus.api.store.ChannelMembershipStore channelMembershipStore,
                           MeterRegistry meterRegistry) {
        this.signalQueue = signalQueue;
        this.config = config;
        this.gateway = gateway;
        this.executor = executor;
        this.batchExecutor = batchExecutor;
        this.cursorStore = cursorStore;
        this.messageStore = messageStore;
        this.channelStore = channelStore;
        this.channelMembershipStore = channelMembershipStore;
        this.meterRegistry = meterRegistry;
    }

    public void start() {
        if (meterRegistry != null) {
            Gauge.builder("qhorus.delivery.backends.unhealthy", unhealthy, Set::size)
                    .register(meterRegistry);
        }
        if (!config.enabled()) {
            LOG.info("Delivery pump disabled (casehub.qhorus.delivery.enabled=false)");
            return;
        }
        running = true;
        executor.execute(this::pumpLoop);
        LOG.info("Delivery pump started");
    }

    public void stop() {
        running = false;
        LOG.info("Delivery pump stopping");
    }

    void pumpLoop() {
        List<UUID> batch = new ArrayList<>();
        while (running) {
            try {
                UUID first = signalQueue.poll(5, TimeUnit.SECONDS);
                if (first != null) {
                    batch.add(first);
                    signalQueue.drainTo(batch);
                    Set<UUID> unique = new HashSet<>(batch);
                    batch.clear();
                    for (UUID channelId : unique) {
                        try {
                            processChannel(channelId);
                        } catch (Exception e) {
                            LOG.errorf(e, "Error processing channel %s — pump continues", channelId);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    void processChannel(UUID channelId) {
        for (ChannelGateway.BackendEntry entry : gateway.trackedEntries(channelId)) {
            if (isUnhealthy(entry.backend().backendId())) {
                continue;
            }
            spawnDelivery(channelId, entry.backend());
        }
    }

    void spawnDelivery(UUID channelId, ChannelBackend backend) {
        String key = channelId + ":" + backend.backendId();
        if (activeDeliveries.add(key)) {
            try {
                executor.execute(() -> {
                    try {
                        deliverPending(channelId, backend);
                    } finally {
                        activeDeliveries.remove(key);
                    }
                });
            } catch (Exception e) {
                activeDeliveries.remove(key);
                LOG.errorf(e, "Failed to submit delivery task for channel %s backend %s",
                        channelId, backend.backendId());
            }
        }
    }

    void deliverPending(UUID channelId, ChannelBackend backend) {
        DeliveryBatchExecutor.BatchResult result = null;
        while (running) {
            result = batchExecutor.deliverBatch(channelId, backend, this);
            if (result.deliveredCount() > 0 && meterRegistry != null) {
                meterRegistry.counter("qhorus.delivery.messages.delivered",
                        "backendId", backend.backendId())
                        .increment(result.deliveredCount());
            }
            if (result.status() == DeliveryBatchExecutor.Status.EMPTY
                    || result.status() == DeliveryBatchExecutor.Status.FAILED) {
                break;
            }
        }
        if (result != null && result.status() == DeliveryBatchExecutor.Status.EMPTY) {
            retryLaggingParticipants(channelId, backend);
        }
    }

    public void reconcileAll() {
        if (!config.enabled() || !running) {
            return;
        }
        List<DeliveryCursor> allCursors = cursorStore.findAll();
        if (meterRegistry != null) {
            computeCursorLag(allCursors);
        }
        Set<UUID> channelIds = new HashSet<>();
        for (DeliveryCursor cursor : allCursors) {
            channelIds.add(cursor.channelId());
        }
        for (UUID channelId : channelIds) {
            for (ChannelGateway.BackendEntry entry : gateway.trackedEntries(channelId)) {
                spawnDelivery(channelId, entry.backend());
            }
        }
    }

    private void computeCursorLag(List<DeliveryCursor> cursors) {
        for (DeliveryCursor cursor : cursors) {
            long head = messageStore.findLastMessage(cursor.channelId())
                    .map(m -> m.id()).orElse(0L);
            long lag = head - cursor.lastDeliveredId();
            Tags tags = Tags.of("backendId", cursor.backendId());
            Gauge.builder("qhorus.delivery.cursor.lag", () -> lag)
                    .tags(tags)
                    .register(meterRegistry);
        }
    }

    @Override
    public void recordFailure(String backendId) {
        int count = consecutiveFailures.merge(backendId, 1, Integer::sum);
        if (count >= config.maxConsecutiveFailures()) {
            unhealthy.add(backendId);
        }
        if (meterRegistry != null) {
            meterRegistry.counter("qhorus.delivery.failures", "backendId", backendId).increment();
        }
    }

    @Override
    public void resetHealth(String backendId) {
        consecutiveFailures.remove(backendId);
        unhealthy.remove(backendId);
    }

    public boolean isUnhealthy(String backendId) {
        return unhealthy.contains(backendId);
    }

    void recordParticipantFailure(String backendId, String memberId) {
        participantFailures.merge(backendId + ":" + memberId, 1, Integer::sum);
    }

    void resetParticipantHealth(String backendId, String memberId) {
        participantFailures.remove(backendId + ":" + memberId);
    }

    boolean isParticipantUnhealthy(String backendId, String memberId) {
        return participantFailures.getOrDefault(
                backendId + ":" + memberId, 0) >= config.maxParticipantConsecutiveFailures();
    }

    void retryLaggingParticipants(UUID channelId, ChannelBackend backend) {
        if (!backend.supportsParticipantDelivery()) { return; }

        java.util.Optional<io.casehub.qhorus.api.channel.Channel> channelOpt = channelStore.findById(channelId);
        if (channelOpt.isEmpty()) { return; }
        if (!io.casehub.qhorus.runtime.channel.ChannelService.isDeliveryTrackingEnabled(channelOpt.get())) { return; }

        java.util.Optional<DeliveryCursor> cursorOpt = cursorStore.findByChannelAndBackend(
                channelId, backend.backendId());
        if (cursorOpt.isEmpty()) { return; }
        DeliveryCursor backendCursor = cursorOpt.get();

        java.util.List<io.casehub.qhorus.api.channel.ChannelMembership> lagging =
                channelMembershipStore.findWithDeliveryLag(channelId, backendCursor.lastDeliveredId());
        lagging = lagging.stream()
                         .filter(m -> io.casehub.platform.api.identity.ActorTypeResolver.resolve(m.memberId()) == backend.actorType())
                         .toList();

        if (lagging.isEmpty()) { return; }

        int retryCount = 0;
        io.casehub.qhorus.api.gateway.ChannelRef ref =
                new io.casehub.qhorus.api.gateway.ChannelRef(channelId, channelOpt.get().name());

        for (io.casehub.qhorus.api.channel.ChannelMembership member : lagging) {
            if (retryCount >= config.maxParticipantRetriesPerCycle()) { break; }
            if (!running) { break; }
            if (isParticipantUnhealthy(backend.backendId(), member.memberId())) { continue; }

            java.util.List<io.casehub.qhorus.api.message.Message> missed = messageStore.scan(
                    io.casehub.qhorus.api.store.query.MessageQuery.builder()
                            .channelId(channelId)
                            .afterId(member.lastDeliveredMessageId())
                            .beforeId(backendCursor.lastDeliveredId())
                            .limit(config.batchSize())
                            .build());

            for (io.casehub.qhorus.api.message.Message m : missed) {
                if (retryCount >= config.maxParticipantRetriesPerCycle()) { break; }
                try {
                    backend.deliverTo(ref, DeliveryBatchExecutor.toOutbound(m), member.memberId());
                    channelMembershipStore.updateLastDeliveredMessageId(
                            channelId, member.memberId(), m.id());
                    retryCount++;
                    resetParticipantHealth(backend.backendId(), member.memberId());
                    if (meterRegistry != null) {
                        meterRegistry.counter("qhorus.delivery.participant.retries",
                                              "backendId", backend.backendId()).increment();
                    }
                } catch (Exception e) {
                    LOG.warnf(e, "Participant retry failed for %s on channel %s message %d",
                              member.memberId(), channelId, m.id());
                    recordParticipantFailure(backend.backendId(), member.memberId());
                    if (meterRegistry != null) {
                        meterRegistry.counter("qhorus.delivery.participant.retry.failures",
                                              "backendId", backend.backendId()).increment();
                    }
                    break;
                }
            }
        }
    }

    Set<String> activeDeliveries() {
        return activeDeliveries;
    }

    Set<String> unhealthySet() {
        return unhealthy;
    }
}
