package io.casehub.qhorus.api.spi.compliance;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.qhorus.api.compliance.ComplianceReportRecordView;
import io.casehub.qhorus.api.compliance.ComplianceScheduleInput;
import io.casehub.qhorus.api.compliance.ComplianceScheduleUpdateInput;
import io.casehub.qhorus.api.compliance.ComplianceScheduleView;
import io.casehub.qhorus.api.compliance.report.AttributionReport;
import io.casehub.qhorus.api.compliance.report.JudgmentAttributionReport;
import io.casehub.qhorus.api.compliance.report.JudgmentFulfillmentReport;
import io.casehub.qhorus.api.compliance.report.ObligationReport;
import io.casehub.qhorus.api.compliance.report.PropertyVerificationReport;
import io.casehub.qhorus.api.compliance.report.ProvenanceReport;
import io.casehub.qhorus.api.compliance.report.TrustHistoryReport;
import io.casehub.qhorus.api.compliance.report.ViolationReport;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "qhorus/compliance", app = "qhorus")
public interface ComplianceApi {

    @PlatformQuery("Get compliance attribution report")
    AttributionReport complianceAttribution(String correlationId, Integer limit);

    @PlatformQuery("Get compliance obligations report")
    ObligationReport complianceObligations(String channelId, String from, String to);

    @PlatformQuery("Get compliance violations report")
    ViolationReport complianceViolations(String channelId, String from, String to);

    @PlatformQuery("Get compliance trust history report")
    TrustHistoryReport complianceTrustHistory(String actorId, String from, String to);

    @PlatformQuery("Get compliance provenance report")
    ProvenanceReport complianceProvenance(String correlationId, Integer limit);

    @PlatformQuery("List compliance reports")
    List<ComplianceReportRecordView> complianceReports(String reportType, Integer limit);

    @PlatformQuery("Get judgment attribution report")
    JudgmentAttributionReport complianceJudgmentAttribution(String judgmentId, Integer limit);

    @PlatformQuery("Get judgment fulfillment report")
    JudgmentFulfillmentReport complianceJudgmentFulfillment(String from, String to, String judgmentType, String actorId);

    @PlatformQuery("Get property verification report")
    PropertyVerificationReport compliancePropertyVerification(String from, String to);

    @PlatformMutation("Create a compliance report schedule")
    ComplianceScheduleView createComplianceSchedule(ComplianceScheduleInput input);

    @PlatformMutation("Update a compliance report schedule")
    ComplianceScheduleView updateComplianceSchedule(ComplianceScheduleUpdateInput input);

    @PlatformMutation("Delete a compliance report schedule")
    boolean deleteComplianceSchedule(UUID id);

    @PlatformMutation("Delete a compliance report")
    boolean deleteComplianceReport(UUID id);
}
