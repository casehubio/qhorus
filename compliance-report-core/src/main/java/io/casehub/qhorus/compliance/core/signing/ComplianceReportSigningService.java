package io.casehub.qhorus.compliance.core.signing;

import io.casehub.platform.api.signing.document.DocumentSigningService;
import io.casehub.platform.api.signing.document.SigningIdentity;
import io.casehub.qhorus.api.compliance.report.ReportFormat;

public class ComplianceReportSigningService {

    private final DocumentSigningService signingService;

    public ComplianceReportSigningService(DocumentSigningService signingService) {
        this.signingService = signingService;
    }

    public SigningResult sign(byte[] reportBytes, ReportFormat format, String tenancyId) {
        var identity = new SigningIdentity("system:compliance-signer", tenancyId);

        return switch (format) {
            case PDF -> signingService.signPdf(reportBytes, identity)
                    .<SigningResult>map(doc -> new SigningResult.Embedded(
                            doc.signedBytes(), doc.signerDn(), doc.signedAt(),
                            doc.keyRef(), doc.profile()))
                    .orElse(new SigningResult.Unsigned());

            case JSON, CSV -> signingService.signDetached(reportBytes, identity)
                    .<SigningResult>map(sig -> new SigningResult.Detached(
                            sig.signatureBytes(), sig.signerDn(), sig.signedAt(),
                            sig.keyRef(), sig.profile()))
                    .orElse(new SigningResult.Unsigned());

            case HTML -> new SigningResult.Unsigned();
        };
    }
}
