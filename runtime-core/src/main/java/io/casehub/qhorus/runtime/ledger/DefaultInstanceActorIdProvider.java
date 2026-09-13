package io.casehub.qhorus.runtime.ledger;

import io.casehub.qhorus.api.spi.InstanceActorIdProvider;

public class DefaultInstanceActorIdProvider implements InstanceActorIdProvider {

    @Override
    public String resolve(final String instanceId) {
        return instanceId;
    }
}
