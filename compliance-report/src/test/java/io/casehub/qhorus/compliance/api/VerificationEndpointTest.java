package io.casehub.qhorus.compliance.api;

import io.casehub.qhorus.compliance.api.core.ComplianceReportCore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationEndpointTest {

    @Mock ComplianceReportCore core;
    @InjectMocks ComplianceReportResource resource;

    @Test
    void verifyStoredReport_notFound_returns404() {
        when(core.verifyStoredReport(any())).thenReturn(Optional.empty());
        var response = resource.verifyStoredReport(UUID.randomUUID());
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void verifyStoredReport_unsignedText_returnsUnsigned() {
        UUID reportId = UUID.randomUUID();
        var verification = new ComplianceVerificationResponse(
                "UNSIGNED", null, null, null, null, java.util.List.of(), null);
        when(core.verifyStoredReport(reportId)).thenReturn(Optional.of(verification));

        var response = resource.verifyStoredReport(reportId);
        assertThat(response.getStatus()).isEqualTo(200);
        var body = (ComplianceVerificationResponse) response.getEntity();
        assertThat(body.status()).isEqualTo("UNSIGNED");
    }

    @Test
    void signatureDownload_noSignature_returns404() {
        UUID reportId = UUID.randomUUID();
        when(core.downloadSignature(reportId)).thenReturn(Optional.empty());

        var response = resource.downloadSignature(reportId);
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void signatureDownload_withSignature_returnsP7s() {
        UUID reportId = UUID.randomUUID();
        when(core.downloadSignature(reportId)).thenReturn(Optional.of(new byte[]{1, 2, 3}));

        var response = resource.downloadSignature(reportId);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaderString("Content-Type")).isEqualTo("application/pkcs7-signature");
        assertThat((byte[]) response.getEntity()).containsExactly(1, 2, 3);
    }
}
