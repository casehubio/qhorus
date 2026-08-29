package io.casehub.qhorus.persistence.memory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.qhorus.api.a2a.PushNotificationConfig;
import io.casehub.qhorus.api.store.CrossTenantPushNotificationConfigStore;
import io.casehub.qhorus.api.store.PushNotificationConfigStore;

@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryPushNotificationConfigStore
        implements PushNotificationConfigStore, CrossTenantPushNotificationConfigStore {

    private final ConcurrentHashMap<UUID, PushNotificationConfig> byId = new ConcurrentHashMap<>();

    @Override
    public void put(PushNotificationConfig config) {
        byId.values().removeIf(c -> c.taskId().equals(config.taskId()) && c.url().equals(config.url()) && !c.id().equals(config.id()));
        byId.put(config.id(), config);
    }

    @Override
    public Optional<PushNotificationConfig> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<PushNotificationConfig> findByTaskId(String taskId) {
        return byId.values().stream()
                .filter(c -> c.taskId().equals(taskId))
                .toList();
    }

    @Override
    public List<PushNotificationConfig> findByChannelId(UUID channelId) {
        return byId.values().stream()
                .filter(c -> c.channelId().equals(channelId))
                .toList();
    }

    @Override
    public Set<String> activeTaskIds() {
        return byId.values().stream()
                .map(PushNotificationConfig::taskId)
                .collect(Collectors.toSet());
    }

    @Override
    public List<PushNotificationConfig> findExpired(Instant threshold) {
        return byId.values().stream()
                .filter(c -> {
                    Instant effectiveTime = c.lastPushedAt() != null ? c.lastPushedAt() : c.createdAt();
                    return effectiveTime.isBefore(threshold);
                })
                .toList();
    }

    @Override
    public void updateLastPushedAt(UUID id, Instant pushedAt) {
        byId.computeIfPresent(id, (k, existing) -> new PushNotificationConfig(
                existing.id(), existing.taskId(), existing.channelId(), existing.url(),
                existing.token(), existing.authScheme(), existing.authCredentialsRef(),
                existing.tenancyId(), existing.createdAt(), pushedAt));
    }

    @Override
    public void delete(UUID id) {
        byId.remove(id);
    }

    @Override
    public void deleteByTaskId(String taskId) {
        byId.values().removeIf(c -> c.taskId().equals(taskId));
    }
}
