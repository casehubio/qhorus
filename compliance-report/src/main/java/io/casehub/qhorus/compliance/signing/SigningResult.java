package io.casehub.qhorus.compliance.signing;

import io.casehub.platform.api.signing.document.SigningProfile;

import java.time.Instant;

public sealed interface SigningResult {

    SignatureStatus status();

    record Embedded(byte[] signedBytes, String signerDn, Instant signedAt,
                    String keyRef, SigningProfile profile) implements SigningResult {
        @Override
        public SignatureStatus status() { return SignatureStatus.SIGNED; }
    }

    record Detached(byte[] signatureBytes, String signerDn, Instant signedAt,
                    String keyRef, SigningProfile profile) implements SigningResult {
        @Override
        public SignatureStatus status() { return SignatureStatus.SIGNED; }
    }

    record Unsigned() implements SigningResult {
        @Override
        public SignatureStatus status() { return SignatureStatus.UNSIGNED; }
    }
}
