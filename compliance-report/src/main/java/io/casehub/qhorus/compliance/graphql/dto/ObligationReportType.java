package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.ObligationReport;
import org.eclipse.microprofile.graphql.Type;

import java.time.Instant;
import java.util.List;

@Type("ObligationReport")
public record ObligationReportType(
        Instant from,
        Instant to,
        List<ChannelObligationSummaryType> channels,
        List<AgentObligationSummaryType> agents,
        int totalCommands,
        int fulfilled,
        int failed,
        int declined,
        int delegated,
        int stillOpen,
        int stalled,
        double overallFulfillmentRate,
        List<PostureEntryType> posture,
        String merkleRoot,
        Instant generatedAt,
        int schemaVersion) {

    public static ObligationReportType from(ObligationReport r) {
        List<PostureEntryType> posture = r.posture() != null
                ? r.posture().entries().stream().map(PostureEntryType::from).toList()
                : List.of();
        return new ObligationReportType(
                r.from(), r.to(),
                r.channels().stream().map(ChannelObligationSummaryType::from).toList(),
                r.agents().stream().map(AgentObligationSummaryType::from).toList(),
                r.totalCommands(), r.fulfilled(), r.failed(), r.declined(),
                r.delegated(), r.stillOpen(), r.stalled(), r.overallFulfillmentRate(),
                posture, r.merkleRoot(), r.generatedAt(), r.schemaVersion());
    }
}
