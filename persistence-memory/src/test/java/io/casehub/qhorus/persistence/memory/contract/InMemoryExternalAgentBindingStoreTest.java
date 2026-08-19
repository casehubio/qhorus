package io.casehub.qhorus.persistence.memory.contract;

import io.casehub.qhorus.api.store.ExternalAgentBindingStore;
import io.casehub.qhorus.persistence.memory.InMemoryExternalAgentBindingStore;

class InMemoryExternalAgentBindingStoreTest extends ExternalAgentBindingStoreContractTest {

    private final InMemoryExternalAgentBindingStore store = new InMemoryExternalAgentBindingStore();

    @Override
    protected ExternalAgentBindingStore store() {
        return store;
    }
}
