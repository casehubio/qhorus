package io.casehub.qhorus.api.spi;

import java.util.Map;
import java.util.Objects;

public record DispatchAdvisory(
        String source,
        Severity severity,
        String message,
        Map<String, Object> evidence,
        SuggestedAction suggestedAction) {

    public DispatchAdvisory {
        Objects.requireNonNull(source);
        Objects.requireNonNull(severity);
        Objects.requireNonNull(message);
        evidence = evidence != null ? Map.copyOf(evidence) : Map.of();
        suggestedAction = suggestedAction != null ? suggestedAction : SuggestedAction.LOG;
    }
}