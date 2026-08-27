package io.casehub.qhorus.compliance.graphql;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.qhorus.compliance.graphql.dto.AttributionReportType;
import io.casehub.qhorus.compliance.graphql.dto.ComplianceReportRecordType;
import io.casehub.qhorus.compliance.graphql.dto.ObligationReportType;
import io.casehub.qhorus.compliance.graphql.dto.ProvenanceReportType;
import io.casehub.qhorus.compliance.graphql.dto.TrustHistoryReportType;
import io.casehub.qhorus.compliance.graphql.dto.JudgmentAttributionReportType;
import io.casehub.qhorus.compliance.graphql.dto.JudgmentFulfillmentReportType;
import io.casehub.qhorus.compliance.graphql.dto.ViolationReportType;
import io.casehub.qhorus.compliance.model.ReportType;
import io.casehub.qhorus.compliance.report.AttributionReportService;
import io.casehub.qhorus.compliance.report.JudgmentAttributionReportService;
import io.casehub.qhorus.compliance.report.JudgmentFulfillmentReportService;
import io.casehub.qhorus.compliance.report.ObligationReportService;
import io.casehub.qhorus.compliance.report.ProvenanceReportService;
import io.casehub.qhorus.compliance.report.TrustHistoryReportService;
import io.casehub.qhorus.compliance.report.ViolationReportService;
import io.casehub.qhorus.compliance.storage.ComplianceReportRecordStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Query;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@GraphQLApi
@McpDomain("qhorus")
@ApplicationScoped
public class ComplianceQueryResolver {

    @Inject AttributionReportService attributionService;
    @Inject ObligationReportService obligationService;
    @Inject ViolationReportService violationService;
    @Inject TrustHistoryReportService trustHistoryService;
    @Inject ProvenanceReportService provenanceService;
    @Inject ComplianceReportRecordStore recordStore;
    @Inject CurrentPrincipal currentPrincipal;
    @Inject
            JudgmentAttributionReportService judgmentAttributionService;
    @Inject
            JudgmentFulfillmentReportService judgmentFulfillmentService;


    @Query
    @Description("Generate an attribution report for an agent causal chain — nodes, edges, trust scores, attestation verdicts")
    public AttributionReportType complianceAttribution(String correlationId, Integer limit) {
        int depth = limit != null ? limit : 100;
        return AttributionReportType.from(
                attributionService.generate(correlationId, depth, currentPrincipal.tenancyId()));
    }

    @Query
    @Description("Generate an obligation fulfillment report — per-channel and per-agent command completion rates for a time window")
    public ObligationReportType complianceObligations(String channelId, String from, String to) {
        UUID chId = channelId != null ? UUID.fromString(channelId) : null;
        Instant f = from != null ? Instant.parse(from) : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant t = to != null ? Instant.parse(to) : Instant.now();
        return ObligationReportType.from(
                obligationService.generate(chId, f, t, null, currentPrincipal.tenancyId()));
    }

    @Query
    @Description("Generate a violation report — enforcement blocks and watchdog alerts for a channel and time window")
    public ViolationReportType complianceViolations(String channelId, String from, String to) {
        UUID chId = channelId != null ? UUID.fromString(channelId) : null;
        Instant f = from != null ? Instant.parse(from) : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant t = to != null ? Instant.parse(to) : Instant.now();
        return ViolationReportType.from(
                violationService.generate(chId, f, t, currentPrincipal.tenancyId()));
    }

    @Query
    @Description("Generate a trust history report — per-actor trust trajectory and attestation summary for a time window")
    public TrustHistoryReportType complianceTrustHistory(String actorId, String from, String to) {
        Instant f = from != null ? Instant.parse(from) : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant t = to != null ? Instant.parse(to) : Instant.now();
        return TrustHistoryReportType.from(
                trustHistoryService.generate(actorId, f, t, currentPrincipal.tenancyId()));
    }

    @Query
    @Description("Generate a PROV-DM provenance report for an agent causal chain — retrieve full PROV-JSON-LD via REST")
    public ProvenanceReportType complianceProvenance(String correlationId, Integer limit) {
        int depth = limit != null ? limit : 100;
        return ProvenanceReportType.from(
                provenanceService.generate(correlationId, depth, currentPrincipal.tenancyId()));
    }

    @Query
    @Description("List stored compliance reports for the current tenant")
    public List<ComplianceReportRecordType> complianceReports(String reportType, Integer limit) {
        int max = limit != null ? limit : 20;
        String tenancyId = currentPrincipal.tenancyId();
        if (reportType != null) {
            return recordStore.findByType(ReportType.valueOf(reportType), tenancyId, max)
                    .stream().map(ComplianceReportRecordType::from).toList();
        }
        return recordStore.findByTimeRange(
                        Instant.now().minus(90, ChronoUnit.DAYS), Instant.now(), tenancyId, max)
                .stream().map(ComplianceReportRecordType::from).toList();
    }

    @Query
    @Description("Generate a judgment attribution report — provenance chain for a single judgment exchange")
    public JudgmentAttributionReportType complianceJudgmentAttribution(String judgmentId, Integer limit) {
        int depth = limit != null ? limit : 200;
        return JudgmentAttributionReportType.from(
                judgmentAttributionService.generate(UUID.fromString(judgmentId), depth, currentPrincipal.tenancyId()));
    }

    @Query
    @Description("Generate a judgment fulfillment report — per-type and per-caller acceptance rates for a time window")
    public JudgmentFulfillmentReportType complianceJudgmentFulfillment(String from, String to, String judgmentType, String actorId) {
        Instant f = from != null ? Instant.parse(from) : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant t = to != null ? Instant.parse(to) : Instant.now();
        return JudgmentFulfillmentReportType.from(
                judgmentFulfillmentService.generate(f, t, judgmentType, actorId, currentPrincipal.tenancyId()));
    }
}
