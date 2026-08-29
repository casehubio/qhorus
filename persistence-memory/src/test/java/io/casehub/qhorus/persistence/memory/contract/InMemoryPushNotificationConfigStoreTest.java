package io.casehub.qhorus.persistence.memory.contract;

import io.casehub.qhorus.api.store.CrossTenantPushNotificationConfigStore;
import io.casehub.qhorus.api.store.PushNotificationConfigStore;
import io.casehub.qhorus.persistence.memory.InMemoryPushNotificationConfigStore;

class InMemoryPushNotificationConfigStoreTest extends PushNotificationConfigStoreContractTest {

    private final InMemoryPushNotificationConfigStore store = new InMemoryPushNotificationConfigStore();

    @Override
    protected PushNotificationConfigStore store() {
        return store;
    }

    @Override
    protected CrossTenantPushNotificationConfigStore crossTenantStore() {
        return store;
    }
}
