package io.casehub.qhorus.persistence.memory;

import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.store.SpaceStore;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Alternative
@Priority(1)
public class InMemorySpaceStore implements SpaceStore {

    private final Map<UUID, Space> store = new ConcurrentHashMap<>();

    @Override
    public Space put(Space space) {
        store.put(space.id(), space);
        return space;
    }

    @Override
    public Optional<Space> find(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Space> findByName(String name) {
        return store.values().stream()
                    .filter(s -> s.name().equals(name))
                    .toList();
    }

    @Override
    public List<Space> listByParent(UUID parentSpaceId) {
        return store.values().stream()
                    .filter(s -> parentSpaceId.equals(s.parentSpaceId()))
                    .toList();
    }

    @Override
    public List<Space> listRoots() {
        return store.values().stream()
                    .filter(s -> s.parentSpaceId() == null)
                    .toList();
    }

    @Override
    public boolean hasChildren(UUID spaceId) {
        return store.values().stream()
                    .anyMatch(s -> spaceId.equals(s.parentSpaceId()));
    }

    @Override
    public void delete(UUID id) {
        store.remove(id);
    }

    public void clear() {
        store.clear();
    }
}
