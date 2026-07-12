package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.channel.Space;
import io.smallrye.mutiny.Uni;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReactiveSpaceStore {
    Uni<Space> put(Space space);

    Uni<Optional<Space>> find(UUID id);

    Uni<List<Space>> findByName(String name);

    Uni<List<Space>> listByParent(UUID parentSpaceId);

    Uni<List<Space>> listRoots();

    Uni<Boolean> hasChildren(UUID spaceId);

    Uni<Void> delete(UUID id);

    Uni<List<Space>> findByIds(Collection<UUID> ids);
}
