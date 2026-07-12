package io.casehub.qhorus.runtime.store.jpa;

import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.store.ReactiveSpaceStore;
import io.casehub.qhorus.runtime.channel.SpaceEntity;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@IfBuildProperty(name = "casehub.qhorus.reactive.enabled", stringValue = "true")
@ApplicationScoped
public class ReactiveJpaSpaceStore implements ReactiveSpaceStore {

    @Inject
    SpaceReactivePanacheRepo repo;

    @Override
    @WithTransaction
    public Uni<Space> put(Space space) {
        SpaceEntity entity = SpaceEntity.fromDomain(space);
        return repo.persist(entity).map(SpaceEntity::toDomain);
    }

    @Override
    public Uni<Optional<Space>> find(UUID id) {
        return repo.findById(id)
                   .map(e -> Optional.ofNullable(e).map(SpaceEntity::toDomain));
    }

    @Override
    public Uni<List<Space>> findByName(String name) {
        return repo.<SpaceEntity>find("name = ?1", name)
                   .list()
                   .map(list -> list.stream().map(SpaceEntity::toDomain).toList());
    }

    @Override
    public Uni<List<Space>> listByParent(UUID parentSpaceId) {
        return repo.<SpaceEntity>find("parentSpaceId = ?1 ORDER BY name", parentSpaceId)
                   .list()
                   .map(list -> list.stream().map(SpaceEntity::toDomain).toList());
    }

    @Override
    public Uni<List<Space>> listRoots() {
        return repo.<SpaceEntity>find("parentSpaceId IS NULL ORDER BY name")
                   .list()
                   .map(list -> list.stream().map(SpaceEntity::toDomain).toList());
    }

    @Override
    public Uni<Boolean> hasChildren(UUID spaceId) {
        return repo.count("parentSpaceId", spaceId).map(count -> count > 0);
    }

    @Override
    @WithTransaction
    public Uni<Void> delete(UUID id) {
        return repo.deleteById(id).replaceWithVoid();
    }

    @Override
    public Uni<List<Space>> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {return Uni.createFrom().item(List.of());}
        return repo.<SpaceEntity>find("id IN ?1", new java.util.ArrayList<>(ids))
                   .list()
                   .map(list -> list.stream().map(SpaceEntity::toDomain).toList());
    }
}
