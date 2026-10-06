package io.casehub.qhorus.runtime.api.core;

import io.casehub.qhorus.api.instance.Instance;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record InstanceResponse(
        String instanceId,
        String description,
        String status,
        Instant lastSeen,
        List<String> capabilities,
        Map<String, String> metadata) {

    public static InstanceResponse from(Instance inst, List<String> capabilities) {
        return new InstanceResponse(
                inst.instanceId(), inst.description(), inst.status(),
                inst.lastSeen(), capabilities, inst.metadata());
    }
}
