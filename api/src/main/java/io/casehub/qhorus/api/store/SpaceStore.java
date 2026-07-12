package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.channel.Space;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceStore {
    Space put(Space space);

    Optional<Space> find(UUID id);

    List<Space> findByName(String name);

    List<Space> listByParent(UUID parentSpaceId);

    List<Space> listRoots();

    boolean hasChildren(UUID spaceId);

    void delete(UUID id);

    default List<Space> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {return List.of();}
        return ids.stream()
                  .map(this::find)
                  .filter(Optional::isPresent)
                  .map(Optional::get)
                  .toList();
    }
}
