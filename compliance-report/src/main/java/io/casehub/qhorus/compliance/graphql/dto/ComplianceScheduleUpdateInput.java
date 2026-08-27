package io.casehub.qhorus.compliance.graphql.dto;

import org.eclipse.microprofile.graphql.Input;

import java.util.UUID;

@Input("ComplianceScheduleUpdateInput")
public record ComplianceScheduleUpdateInput(
        UUID id,
        Boolean enabled,
        String scheduleJson) {
}
