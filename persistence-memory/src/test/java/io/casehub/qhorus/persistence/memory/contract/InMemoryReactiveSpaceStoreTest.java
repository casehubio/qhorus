package io.casehub.qhorus.persistence.memory.contract;

import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.persistence.memory.InMemoryReactiveSpaceStore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class InMemoryReactiveSpaceStoreTest extends SpaceStoreContractTest {

    private final InMemoryReactiveSpaceStore store = new InMemoryReactiveSpaceStore();

    @Override
    protected Space put(Space s)                           {return store.put(s).await().indefinitely();}

    @Override
    protected Optional<Space> find(UUID id)                {return store.find(id).await().indefinitely();}

    @Override
    protected List<Space> findByName(String name)          {return store.findByName(name).await().indefinitely();}

    @Override
    protected List<Space> listByParent(UUID parentSpaceId) {return store.listByParent(parentSpaceId).await().indefinitely();}

    @Override
    protected List<Space> listRoots()                      {return store.listRoots().await().indefinitely();}

    @Override
    protected boolean hasChildren(UUID spaceId)            {return store.hasChildren(spaceId).await().indefinitely();}

    @Override
    protected void delete(UUID id)                         {store.delete(id).await().indefinitely();}
}
