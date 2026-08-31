package io.casehub.qhorus.compliance.api;

import java.util.List;

public record ComplianceVerificationResponse(
        String status,
        String signerDn,
        String signedAt,
        String keyRef,
        String detectedProfile,
        List<CertificateInfoDto> certificateChain,
        String diagnosticMessage) {

    public record CertificateInfoDto(
            String subjectDn,
            String issuerDn,
            String validFrom,
            String validTo,
            boolean claimsQualified) {}
}
