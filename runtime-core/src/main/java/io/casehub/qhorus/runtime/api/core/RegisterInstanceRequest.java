package io.casehub.qhorus.runtime.api.core;

import java.util.List;
import java.util.Map;

public record RegisterInstanceRequest(
        String instanceId,
        String description,
        List<String> capabilities,
        Map<String, String> metadata) {}
