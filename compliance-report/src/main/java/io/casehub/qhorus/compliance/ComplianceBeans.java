package io.casehub.qhorus.compliance;

import io.casehub.platform.api.signing.document.DocumentVerificationService;
import io.casehub.qhorus.compliance.api.core.ComplianceReportCore;
import io.casehub.qhorus.compliance.api.core.ComplianceScheduleCore;
import io.casehub.qhorus.compliance.format.CsvReportRenderer;
import io.casehub.qhorus.compliance.format.HtmlReportRenderer;
import io.casehub.qhorus.compliance.format.JsonReportRenderer;
import io.casehub.qhorus.compliance.format.PdfReportRenderer;
import io.casehub.qhorus.compliance.report.AttributionReportService;
import io.casehub.qhorus.compliance.report.JudgmentAttributionReportService;
import io.casehub.qhorus.compliance.report.JudgmentFulfillmentReportService;
import io.casehub.qhorus.compliance.report.ObligationReportService;
import io.casehub.qhorus.compliance.report.ProvenanceReportService;
import io.casehub.qhorus.compliance.report.TrustHistoryReportService;
import io.casehub.qhorus.compliance.report.ViolationReportService;
import io.casehub.qhorus.compliance.schedule.ComplianceReportScheduleStore;
import io.casehub.qhorus.compliance.storage.ComplianceReportRecordStore;
import io.casehub.qhorus.compliance.storage.ComplianceReportStorageService;
import io.casehub.qhorus.compliance.verification.PropertyVerificationService;
import io.casehub.qhorus.runtime.data.DataService;
import io.casehub.qhorus.runtime.identity.InboundTenancyContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ComplianceBeans {

    @Produces
    @ApplicationScoped
    public ComplianceScheduleCore complianceScheduleCore(ComplianceReportScheduleStore scheduleStore,
                                                          InboundTenancyContext tenancyContext) {
        return new ComplianceScheduleCore(scheduleStore, tenancyContext);
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
            ComplianceReportRecordStore recordStore,
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
                verificationService, dataService, tenancyContext,
                judgmentAttributionService, judgmentFulfillmentService,
                propertyVerificationService);
    }
}
