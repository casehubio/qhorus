package io.casehub.qhorus.persistence.memory;

import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.store.ReactiveSpaceStore;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Alternative
@Priority(1)
public class InMemoryReactiveSpaceStore implements ReactiveSpaceStore {

    private final InMemorySpaceStore delegate = new InMemorySpaceStore();

    @Override
    public Uni<Space> put(Space space) {
        return Uni.createFrom().item(() -> delegate.put(space));
    }

    @Override
    public Uni<Optional<Space>> find(UUID id) {
        return Uni.createFrom().item(() -> delegate.find(id));
    }

    @Override
    public Uni<List<Space>> findByName(String name) {
        return Uni.createFrom().item(() -> delegate.findByName(name));
    }

    @Override
    public Uni<List<Space>> listByParent(UUID parentSpaceId) {
        return Uni.createFrom().item(() -> delegate.listByParent(parentSpaceId));
    }

    @Override
    public Uni<List<Space>> listRoots() {
        return Uni.createFrom().item(delegate::listRoots);
    }

    @Override
    public Uni<Boolean> hasChildren(UUID spaceId) {
        return Uni.createFrom().item(() -> delegate.hasChildren(spaceId));
    }

    @Override
    public Uni<Void> delete(UUID id) {
        return Uni.createFrom().item(() -> {
            delegate.delete(id);
            return null;
        });
    }

    @Override
    public Uni<List<Space>> findByIds(Collection<UUID> ids) {
        return Uni.createFrom().item(() -> delegate.findByIds(ids));
    }

    public void clear() {
        delegate.clear();
    }
}
