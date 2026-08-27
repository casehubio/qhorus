package io.casehub.qhorus.compliance.model;

import io.casehub.qhorus.api.spi.compliance.CompliancePosture;

import java.time.Instant;
import java.util.List;

public record ObligationReport(
        Instant from,
        Instant to,
        List<ChannelObligationSummary> channels,
        List<AgentObligationSummary> agents,
        int totalCommands,
        int fulfilled,
        int failed,
        int declined,
        int delegated,
        int stillOpen,
        int stalled,
        double overallFulfillmentRate,
        CompliancePosture posture,
        String merkleRoot,
        Instant generatedAt,
        int schemaVersion) {
}
