package io.casehub.qhorus.api.compliance;

import java.util.UUID;

public record ComplianceScheduleUpdateInput(
    UUID id,
    Boolean enabled,
    String scheduleJson
) {}
