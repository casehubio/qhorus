package io.casehub.qhorus.compliance.api;

import io.casehub.platform.api.signing.document.DocumentVerificationResult;
import io.casehub.platform.api.signing.document.DocumentVerificationService;
import io.casehub.platform.api.signing.document.VerificationStatus;
import io.casehub.qhorus.compliance.model.ReportFormat;
import io.casehub.qhorus.compliance.model.ReportType;
import io.casehub.qhorus.compliance.storage.ComplianceReportRecord;
import io.casehub.qhorus.compliance.storage.ComplianceReportRecordStore;
import io.casehub.qhorus.runtime.data.DataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationEndpointTest {

    @Mock ComplianceReportRecordStore recordStore;
    @Mock DataService dataService;
    @Mock DocumentVerificationService verificationService;
    @InjectMocks ComplianceReportResource resource;

    @Test
    void verifyStoredReport_notFound_returns404() {
        when(recordStore.findById(any())).thenReturn(Optional.empty());
        var response = resource.verifyStoredReport(UUID.randomUUID());
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void verifyStoredReport_unsignedText_returnsUnsigned() {
        UUID reportId = UUID.randomUUID();
        UUID artefactId = UUID.randomUUID();

        var record = new ComplianceReportRecord();
        record.id = reportId;
        record.artefactId = artefactId;
        record.reportType = ReportType.OBLIGATION;
        record.format = ReportFormat.JSON;
        record.tenancyId = "t1";
        record.generatedAt = Instant.now();
        when(recordStore.findById(reportId)).thenReturn(Optional.of(record));

        var data = io.casehub.qhorus.api.data.SharedData.builder("k")
                .id(artefactId).content("{\"test\":true}").complete(true).build();
        when(dataService.getByUuid(artefactId)).thenReturn(Optional.of(data));

        var response = resource.verifyStoredReport(reportId);
        assertThat(response.getStatus()).isEqualTo(200);
        var body = (ComplianceVerificationResponse) response.getEntity();
        assertThat(body.status()).isEqualTo("UNSIGNED");
    }

    @Test
    void signatureDownload_noSignature_returns404() {
        UUID reportId = UUID.randomUUID();
        var record = new ComplianceReportRecord();
        record.id = reportId;
        record.signatureArtefactId = null;
        record.reportType = ReportType.OBLIGATION;
        record.format = ReportFormat.JSON;
        record.tenancyId = "t1";
        record.generatedAt = Instant.now();
        when(recordStore.findById(reportId)).thenReturn(Optional.of(record));

        var response = resource.downloadSignature(reportId);
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void signatureDownload_withSignature_returnsP7s() {
        UUID reportId = UUID.randomUUID();
        UUID sigArtefactId = UUID.randomUUID();

        var record = new ComplianceReportRecord();
        record.id = reportId;
        record.signatureArtefactId = sigArtefactId;
        record.reportType = ReportType.OBLIGATION;
        record.format = ReportFormat.JSON;
        record.tenancyId = "t1";
        record.generatedAt = Instant.now();
        when(recordStore.findById(reportId)).thenReturn(Optional.of(record));

        var sigData = io.casehub.qhorus.api.data.SharedData.builder("k.p7s")
                .id(sigArtefactId).binaryContent(new byte[]{1, 2, 3}).complete(true).build();
        when(dataService.getByUuid(sigArtefactId)).thenReturn(Optional.of(sigData));

        var response = resource.downloadSignature(reportId);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaderString("Content-Type")).isEqualTo("application/pkcs7-signature");
        assertThat((byte[]) response.getEntity()).containsExactly(1, 2, 3);
    }
}
