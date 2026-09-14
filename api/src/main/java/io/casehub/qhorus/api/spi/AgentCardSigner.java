package io.casehub.qhorus.api.spi;

public interface AgentCardSigner {

    String sign(String cardJson);

    VerificationResult verify(String signedCardJson);

    String jwks();

    record VerificationResult(boolean verified, String keyId, String error) {}
}
