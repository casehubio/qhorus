package io.casehub.qhorus.compliance;

import io.casehub.platform.api.signing.document.DocumentVerificationService;
import io.casehub.qhorus.compliance.core.api.ComplianceReportCore;
import io.casehub.qhorus.compliance.core.api.ComplianceScheduleCore;
import io.casehub.qhorus.compliance.core.format.CsvReportRenderer;
import io.casehub.qhorus.compliance.core.format.HtmlReportRenderer;
import io.casehub.qhorus.compliance.core.format.JsonReportRenderer;
import io.casehub.qhorus.compliance.core.format.PdfReportRenderer;
import io.casehub.qhorus.compliance.core.report.AttributionReportService;
import io.casehub.qhorus.compliance.core.report.JudgmentAttributionReportService;
import io.casehub.qhorus.compliance.core.report.JudgmentFulfillmentReportService;
import io.casehub.qhorus.compliance.core.report.ObligationReportService;
import io.casehub.qhorus.compliance.core.report.ProvenanceReportService;
import io.casehub.qhorus.compliance.core.report.TrustHistoryReportService;
import io.casehub.qhorus.compliance.core.report.ViolationReportService;
import io.casehub.qhorus.compliance.core.storage.ComplianceReportStorageService;
import io.casehub.qhorus.compliance.core.verification.PropertyVerificationService;
import io.casehub.qhorus.runtime.data.DataService;
import io.casehub.qhorus.runtime.identity.InboundTenancyContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ComplianceBeans {

    @Produces
    @ApplicationScoped
    public ComplianceScheduleCore complianceScheduleCore(
            io.casehub.qhorus.compliance.core.schedule.ComplianceReportScheduleStore scheduleStore,
            InboundTenancyContext tenancyContext) {
        return new ComplianceScheduleCore(scheduleStore, tenancyContext::tenancyId);
    }

    @Produces
    @ApplicationScoped
    public ComplianceReportCore complianceReportCore(
            AttributionReportService attributionService,
            ObligationReportService obligationService,
            TrustHistoryReportService trustHistoryService,
            ViolationReportService violationService,
            ProvenanceReportService provenanceService,
            ComplianceReportStorageService storageService,
            io.casehub.qhorus.compliance.core.storage.ComplianceReportRecordStore recordStore,
            JsonReportRenderer jsonRenderer,
            CsvReportRenderer csvRenderer,
            HtmlReportRenderer htmlRenderer,
            PdfReportRenderer pdfRenderer,
            DocumentVerificationService verificationService,
            DataService dataService,
            InboundTenancyContext tenancyContext,
            JudgmentAttributionReportService judgmentAttributionService,
            JudgmentFulfillmentReportService judgmentFulfillmentService,
            PropertyVerificationService propertyVerificationService) {
        return new ComplianceReportCore(
                attributionService, obligationService, trustHistoryService,
                violationService, provenanceService, storageService, recordStore,
                jsonRenderer, csvRenderer, htmlRenderer, pdfRenderer,
                verificationService, dataService, tenancyContext::tenancyId,
                judgmentAttributionService, judgmentFulfillmentService,
                propertyVerificationService);
    }
}
