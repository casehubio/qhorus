package io.casehub.qhorus.runtime.store.jpa;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.store.SpaceStore;
import io.casehub.qhorus.runtime.channel.SpaceEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaSpaceStore implements SpaceStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    @Transactional
    public Space put(Space space) {
        SpaceEntity entity = SpaceEntity.fromDomain(space);
        if (entity.id != null && SpaceEntity.<SpaceEntity>findByIdOptional(entity.id).isPresent()) {
            entity = SpaceEntity.getEntityManager().merge(entity);
            SpaceEntity.flush();
        } else {
            entity.persistAndFlush();
        }
        return entity.toDomain();
    }

    @Override
    public Optional<Space> find(UUID id) {
        return SpaceEntity.<SpaceEntity>find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                          .firstResultOptional()
                          .map(SpaceEntity::toDomain);
    }

    @Override
    public List<Space> findByName(String name) {
        return SpaceEntity.<SpaceEntity>find("name = ?1 AND tenancyId = ?2", name, currentPrincipal.tenancyId())
                          .list()
                          .stream()
                          .map(SpaceEntity::toDomain)
                          .toList();
    }

    @Override
    public List<Space> listByParent(UUID parentSpaceId) {
        return SpaceEntity.<SpaceEntity>find(
                                  "parentSpaceId = ?1 AND tenancyId = ?2 ORDER BY name",
                                  parentSpaceId, currentPrincipal.tenancyId())
                          .list()
                          .stream()
                          .map(SpaceEntity::toDomain)
                          .toList();
    }

    @Override
    public List<Space> listRoots() {
        return SpaceEntity.<SpaceEntity>find(
                                  "parentSpaceId IS NULL AND tenancyId = ?1 ORDER BY name", currentPrincipal.tenancyId())
                          .list()
                          .stream()
                          .map(SpaceEntity::toDomain)
                          .toList();
    }

    @Override
    public boolean hasChildren(UUID spaceId) {
        return SpaceEntity.count("parentSpaceId = ?1 AND tenancyId = ?2",
                                 spaceId, currentPrincipal.tenancyId()) > 0;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        SpaceEntity.delete("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId());
    }

    @Override
    public List<Space> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {return List.of();}
        List<SpaceEntity> entities = SpaceEntity.list("id IN ?1 AND tenancyId = ?2",
                                                      new java.util.ArrayList<>(ids), currentPrincipal.tenancyId());
        return entities.stream().map(SpaceEntity::toDomain).toList();
    }
}
