package io.casehub.qhorus.api.instance;

import java.util.List;

public record InstanceDeregisteredEvent(
        String instanceId,
        List<String> capabilities) {

    public InstanceDeregisteredEvent {
        capabilities = capabilities != null ? List.copyOf(capabilities) : List.of();
    }
}
