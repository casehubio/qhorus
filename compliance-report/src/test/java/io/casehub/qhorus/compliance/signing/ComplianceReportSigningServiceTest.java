package io.casehub.qhorus.compliance.signing;

import io.casehub.platform.api.signing.document.DetachedSignature;
import io.casehub.platform.api.signing.document.DocumentSigningService;
import io.casehub.platform.api.signing.document.SignedDocument;
import io.casehub.platform.api.signing.document.SigningIdentity;
import io.casehub.platform.api.signing.document.SigningProfile;
import io.casehub.qhorus.compliance.model.ReportFormat;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ComplianceReportSigningServiceTest {

    @Test
    void pdf_withSigner_returnsEmbedded() {
        var dss = mock(DocumentSigningService.class);
        when(dss.signPdf(any(), any())).thenReturn(Optional.of(
                new SignedDocument(new byte[]{9}, "CN=Seal", Instant.now(), "kr", SigningProfile.B_T)));
        var service = new ComplianceReportSigningService(dss);
        var result = service.sign(new byte[]{1}, ReportFormat.PDF, "tenant");
        assertThat(result.status()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(result).isInstanceOf(SigningResult.Embedded.class);
        var embedded = (SigningResult.Embedded) result;
        assertThat(embedded.signedBytes()).containsExactly(9);
        assertThat(embedded.signerDn()).isEqualTo("CN=Seal");
    }

    @Test
    void json_withSigner_returnsDetached() {
        var dss = mock(DocumentSigningService.class);
        when(dss.signDetached(any(), any())).thenReturn(Optional.of(
                new DetachedSignature(new byte[]{8}, "CN=Seal", Instant.now(), "kr", SigningProfile.B_T)));
        var service = new ComplianceReportSigningService(dss);
        var result = service.sign(new byte[]{1}, ReportFormat.JSON, "tenant");
        assertThat(result.status()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(result).isInstanceOf(SigningResult.Detached.class);
        var detached = (SigningResult.Detached) result;
        assertThat(detached.signatureBytes()).containsExactly(8);
    }

    @Test
    void csv_withSigner_returnsDetached() {
        var dss = mock(DocumentSigningService.class);
        when(dss.signDetached(any(), any())).thenReturn(Optional.of(
                new DetachedSignature(new byte[]{7}, "CN=Seal", Instant.now(), "kr", SigningProfile.B_T)));
        var service = new ComplianceReportSigningService(dss);
        var result = service.sign(new byte[]{1}, ReportFormat.CSV, "tenant");
        assertThat(result.status()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(result).isInstanceOf(SigningResult.Detached.class);
    }

    @Test
    void html_alwaysUnsigned() {
        var dss = mock(DocumentSigningService.class);
        var service = new ComplianceReportSigningService(dss);
        var result = service.sign(new byte[]{1}, ReportFormat.HTML, "tenant");
        assertThat(result.status()).isEqualTo(SignatureStatus.UNSIGNED);
        assertThat(result).isInstanceOf(SigningResult.Unsigned.class);
        verifyNoInteractions(dss);
    }

    @Test
    void noOp_returnsUnsigned() {
        var dss = mock(DocumentSigningService.class);
        when(dss.signPdf(any(), any())).thenReturn(Optional.empty());
        var service = new ComplianceReportSigningService(dss);
        var result = service.sign(new byte[]{1}, ReportFormat.PDF, "tenant");
        assertThat(result.status()).isEqualTo(SignatureStatus.UNSIGNED);
    }

    @Test
    void noOp_detached_returnsUnsigned() {
        var dss = mock(DocumentSigningService.class);
        when(dss.signDetached(any(), any())).thenReturn(Optional.empty());
        var service = new ComplianceReportSigningService(dss);
        var result = service.sign(new byte[]{1}, ReportFormat.JSON, "tenant");
        assertThat(result.status()).isEqualTo(SignatureStatus.UNSIGNED);
    }
}
