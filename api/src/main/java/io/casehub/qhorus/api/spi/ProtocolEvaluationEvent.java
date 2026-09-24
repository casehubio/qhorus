package io.casehub.qhorus.api.spi;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ProtocolEvaluationEvent(
        UUID channelId,
        String channelName,
        String tenancyId,
        List<DispatchAdvisory> violations,
        EnforcementOutcome enforcementOutcome) {

    public ProtocolEvaluationEvent {
        Objects.requireNonNull(channelId);
        Objects.requireNonNull(channelName);
        Objects.requireNonNull(enforcementOutcome);
        violations = violations != null ? List.copyOf(violations) : List.of();
    }

    public enum EnforcementOutcome {
        ALLOWED,
        BLOCKED,
        QUARANTINED
    }
}
