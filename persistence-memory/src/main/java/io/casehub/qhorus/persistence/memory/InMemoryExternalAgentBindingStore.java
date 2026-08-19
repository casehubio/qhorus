package io.casehub.qhorus.persistence.memory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.qhorus.api.instance.ExternalAgentBinding;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;

@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryExternalAgentBindingStore implements ExternalAgentBindingStore {

    private final ConcurrentHashMap<String, ExternalAgentBinding> byInstanceId = new ConcurrentHashMap<>();

    @Override
    public void put(ExternalAgentBinding binding) {
        byInstanceId.put(binding.instanceId(), binding);
    }

    @Override
    public Optional<ExternalAgentBinding> findByInstanceId(String instanceId) {
        return Optional.ofNullable(byInstanceId.get(instanceId));
    }

    @Override
    public List<ExternalAgentBinding> findAll() {
        return List.copyOf(byInstanceId.values());
    }

    @Override
    public void delete(UUID id) {
        byInstanceId.values().removeIf(b -> b.id().equals(id));
    }

    @Override
    public void deleteByInstanceId(String instanceId) {
        byInstanceId.remove(instanceId);
    }
}
