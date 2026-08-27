package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.ViolationReport;
import org.eclipse.microprofile.graphql.Type;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Type("ViolationReport")
public record ViolationReportType(
        Instant from,
        Instant to,
        UUID channelId,
        String channelName,
        List<ViolationEntryType> violations,
        int totalBlocked,
        int totalAdvisory,
        int totalQuarantined,
        String merkleRoot,
        Instant generatedAt,
        int schemaVersion) {

    public static ViolationReportType from(ViolationReport r) {
        return new ViolationReportType(
                r.from(), r.to(), r.channelId(), r.channelName(),
                r.violations().stream().map(ViolationEntryType::from).toList(),
                r.totalBlocked(), r.totalAdvisory(), r.totalQuarantined(),
                r.merkleRoot(), r.generatedAt(), r.schemaVersion());
    }
}
