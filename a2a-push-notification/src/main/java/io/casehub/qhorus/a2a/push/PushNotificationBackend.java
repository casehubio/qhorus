package io.casehub.qhorus.a2a.push;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.a2a.PushNotificationConfig;
import io.casehub.qhorus.api.a2a.PushNotificationRegistrar;
import io.casehub.qhorus.api.gateway.BackendRegistry;
import io.casehub.qhorus.api.gateway.ChannelBackend;
import io.casehub.qhorus.api.gateway.ChannelInitialisedEvent;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.DeliveryGuarantee;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.CrossTenantPushNotificationConfigStore;

@ApplicationScoped
public class PushNotificationBackend implements ChannelBackend, PushNotificationRegistrar {

    private static final Logger LOG = Logger.getLogger(PushNotificationBackend.class);

    private static final Set<MessageType> PUSH_RELEVANT = Set.of(
            MessageType.STATUS, MessageType.DONE, MessageType.FAILURE,
            MessageType.DECLINE, MessageType.HANDOFF);

    private static final Set<MessageType> TERMINAL = Set.of(
            MessageType.DONE, MessageType.FAILURE, MessageType.DECLINE);

    private static final Map<MessageType, String> STATE_MAP = Map.of(
            MessageType.STATUS, "working",
            MessageType.HANDOFF, "working",
            MessageType.DONE, "completed",
            MessageType.FAILURE, "failed",
            MessageType.DECLINE, "canceled");

    private final CrossTenantPushNotificationConfigStore store;
    private final PushNotificationPoster poster;

    private final Set<String> activeTaskIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, UrlHealthState> urlHealth = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingTerminal> pendingTerminals = new ConcurrentHashMap<>();

    int maxUrlFailures = 5;
    Clock clock = Clock.systemUTC();

    @Inject
    BackendRegistry backendRegistry;

    record PendingTerminal(PushNotificationConfig config, String taskState, String content, UUID channelId) {}

    @Inject
    public PushNotificationBackend(CrossTenantPushNotificationConfigStore store,
                                   PushNotificationPoster poster) {
        this.store = store;
        this.poster = poster;
    }

    @Override
    public String backendId() {
        return "a2a-push";
    }

    @Override
    public ActorType actorType() {
        return ActorType.AGENT;
    }

    @Override
    public DeliveryGuarantee deliveryGuarantee() {
        return DeliveryGuarantee.AT_LEAST_ONCE;
    }

    @Override
    public void open(ChannelRef channel, Map<String, String> metadata) {
    }

    @Override
    public void post(ChannelRef channel, OutboundMessage message) {
        try {
            retryPendingTerminals(channel.id());
            doPost(channel, message);
        } catch (Exception e) {
            LOG.warnf("Push backend post() caught exception (non-throwing contract): %s", e.getMessage());
        }
    }

    @Override
    public void close(ChannelRef channel) {
    }

    void onChannelRecovery(@Observes ChannelInitialisedEvent event) {
        if (backendRegistry == null) {
            return;
        }
        List<PushNotificationConfig> configs = store.findByChannelId(event.channelId());
        if (!configs.isEmpty()) {
            backendRegistry.registerBackend(event.channelId(), this, "agent");
            for (PushNotificationConfig cfg : configs) {
                activeTaskIds.add(cfg.taskId());
            }
        }
    }

    @Override
    public void onConfigCreated(UUID channelId, String taskId) {
        activeTaskIds.add(taskId);
        if (backendRegistry != null) {
            backendRegistry.registerBackend(channelId, this, "agent");
        }
    }

    private void doPost(ChannelRef channel, OutboundMessage message) {
        MessageType type = message.type();
        if (type == null) {
            return;
        }

        if (!PUSH_RELEVANT.contains(type)) {
            return;
        }

        String correlationId = message.correlationId();
        if (correlationId == null) {
            return;
        }

        List<PushNotificationConfig> configs = resolveConfigs(correlationId);
        if (configs.isEmpty()) {
            return;
        }

        String taskState = STATE_MAP.get(type);
        boolean isTerminal = TERMINAL.contains(type);

        for (PushNotificationConfig config : configs) {
            pushToConfig(config, taskState, message.content(), channel.id(), isTerminal);
        }
    }

    private void pushToConfig(PushNotificationConfig config, String taskState,
                              String content, UUID channelId, boolean isTerminal) {
        UrlHealthState health = urlHealth.get(config.url());
        if (health != null && health.isWithinBackoff(clock.instant())) {
            if (isTerminal) {
                trackPendingTerminal(config, taskState, content, channelId);
            }
            return;
        }

        PushPostResult result = poster.push(config, taskState, content, channelId);
        if (result.success()) {
            urlHealth.remove(config.url());
            store.updateLastPushedAt(config.id(), clock.instant());
            if (isTerminal) {
                store.delete(config.id());
                activeTaskIds.remove(config.taskId());
                pendingTerminals.remove(config.taskId() + ":" + config.url());
            }
        } else {
            recordUrlFailure(config, taskState, content, channelId, isTerminal);
        }
    }

    private void recordUrlFailure(PushNotificationConfig config, String taskState,
                                  String content, UUID channelId, boolean isTerminal) {
        Instant now = clock.instant();
        UrlHealthState updated = urlHealth.compute(config.url(), (url, existing) ->
                existing == null ? UrlHealthState.initial(now) : existing.recordFailure(now));

        if (updated.failures() >= maxUrlFailures) {
            LOG.warnf("Push URL exhausted after %d failures, deleting config %s for task %s: %s",
                    maxUrlFailures, config.id(), config.taskId(), config.url());
            store.delete(config.id());
            activeTaskIds.remove(config.taskId());
            urlHealth.remove(config.url());
            pendingTerminals.remove(config.taskId() + ":" + config.url());
        } else if (isTerminal) {
            trackPendingTerminal(config, taskState, content, channelId);
        }
    }

    private void trackPendingTerminal(PushNotificationConfig config, String taskState,
                                      String content, UUID channelId) {
        pendingTerminals.put(config.taskId() + ":" + config.url(),
                new PendingTerminal(config, taskState, content, channelId));
    }

    private void retryPendingTerminals(UUID channelId) {
        if (pendingTerminals.isEmpty()) {
            return;
        }
        var iterator = pendingTerminals.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            PendingTerminal pending = entry.getValue();
            if (!pending.channelId().equals(channelId)) {
                continue;
            }
            UrlHealthState health = urlHealth.get(pending.config().url());
            if (health != null && health.isWithinBackoff(clock.instant())) {
                continue;
            }
            PushPostResult result = poster.push(pending.config(), pending.taskState(),
                    pending.content(), pending.channelId());
            if (result.success()) {
                urlHealth.remove(pending.config().url());
                store.delete(pending.config().id());
                activeTaskIds.remove(pending.config().taskId());
                iterator.remove();
            } else {
                recordUrlFailure(pending.config(), pending.taskState(),
                        pending.content(), pending.channelId(), true);
            }
        }
    }

    private List<PushNotificationConfig> resolveConfigs(String correlationId) {
        if (activeTaskIds.contains(correlationId)) {
            return store.findByTaskId(correlationId);
        }
        List<PushNotificationConfig> configs = store.findByTaskId(correlationId);
        if (!configs.isEmpty()) {
            activeTaskIds.add(correlationId);
        }
        return configs;
    }
}
