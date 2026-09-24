package io.casehub.qhorus.api.instance;

import java.util.List;

public record InstanceRegisteredEvent(
        String instanceId,
        List<String> previousCapabilities,
        List<String> currentCapabilities) {

    public InstanceRegisteredEvent {
        previousCapabilities = previousCapabilities != null ? List.copyOf(previousCapabilities) : List.of();
        currentCapabilities = currentCapabilities != null ? List.copyOf(currentCapabilities) : List.of();
    }
}
