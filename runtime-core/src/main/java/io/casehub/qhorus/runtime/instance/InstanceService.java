package io.casehub.qhorus.runtime.instance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.transaction.Transactional;

import io.casehub.qhorus.api.instance.Instance;
import io.casehub.qhorus.api.store.InstanceStore;
import io.casehub.qhorus.api.store.query.InstanceQuery;

public class InstanceService {

    private final InstanceStore instanceStore;

    public InstanceService(InstanceStore instanceStore) {
        this.instanceStore = instanceStore;
    }

    @Transactional
    public Instance register(String instanceId, String description, List<String> capabilityTags) {
        return register(instanceId, description, capabilityTags, null, false);
    }

    @Transactional
    public Instance register(String instanceId, String description, List<String> capabilityTags,
                             String claudonySessionId) {
        return register(instanceId, description, capabilityTags, claudonySessionId, false);
    }

    @Transactional
    public Instance register(String instanceId, String description, List<String> capabilityTags,
                             String claudonySessionId, boolean readOnly) {
        Instance existing = instanceStore.findByInstanceId(instanceId).orElse(null);

        Instance.Builder b;
        if (existing == null) {
            b = Instance.builder(instanceId);
        } else {
            b = existing.toBuilder();
        }
        Instance instance = b.description(description)
                .status("online")
                .lastSeen(Instant.now())
                .claudonySessionId(claudonySessionId)
                .readOnly(readOnly)
                .build();
        Instance saved = instanceStore.put(instance);

        instanceStore.putCapabilities(saved.id(), capabilityTags);

        return saved;
    }

    @Transactional
    public void heartbeat(String instanceId) {
        instanceStore.findByInstanceId(instanceId).ifPresent(instance -> {
            instanceStore.put(instance.toBuilder()
                    .lastSeen(Instant.now())
                    .status("online")
                    .build());
        });
    }

    public Optional<Instance> findByInstanceId(String instanceId) {
        return instanceStore.findByInstanceId(instanceId);
    }

    public List<Instance> findByCapability(String tag) {
        return instanceStore.scan(InstanceQuery.byCapability(tag));
    }

    public List<String> findCapabilityTagsForInstance(String instanceId) {
        return instanceStore.findByInstanceId(instanceId)
                .map(i -> instanceStore.findCapabilities(i.id()))
                .orElse(List.of());
    }

    public List<Instance> listAll() {
        return instanceStore.scan(InstanceQuery.all());
    }

    @Transactional
    public void deregister(String instanceId) {
        instanceStore.findByInstanceId(instanceId)
                .ifPresent(inst -> instanceStore.delete(inst.id()));
    }

    @Transactional
    public void markStaleOlderThan(int thresholdSeconds) {
        Instant cutoff = Instant.now().minusSeconds(thresholdSeconds);
        instanceStore.scan(InstanceQuery.all()).stream()
                .filter(i -> "online".equals(i.status()))
                .filter(i -> i.lastSeen() != null && i.lastSeen().isBefore(cutoff))
                .forEach(i -> instanceStore.put(i.toBuilder().status("stale").build()));
    }

    @Transactional
    public void markOffline(String instanceId) {
        instanceStore.findByInstanceId(instanceId)
                .ifPresent(i -> instanceStore.put(i.toBuilder().status("offline").build()));
    }
}
