package io.casehub.qhorus.persistence.memory.contract;

import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.persistence.memory.InMemorySpaceStore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class InMemorySpaceStoreTest extends SpaceStoreContractTest {

    private final InMemorySpaceStore store = new InMemorySpaceStore();

    @Override
    protected Space put(Space s)                           {return store.put(s);}

    @Override
    protected Optional<Space> find(UUID id)                {return store.find(id);}

    @Override
    protected List<Space> findByName(String name)          {return store.findByName(name);}

    @Override
    protected List<Space> listByParent(UUID parentSpaceId) {return store.listByParent(parentSpaceId);}

    @Override
    protected List<Space> listRoots()                      {return store.listRoots();}

    @Override
    protected boolean hasChildren(UUID spaceId)            {return store.hasChildren(spaceId);}

    @Override
    protected void delete(UUID id)                         {store.delete(id);}
}
