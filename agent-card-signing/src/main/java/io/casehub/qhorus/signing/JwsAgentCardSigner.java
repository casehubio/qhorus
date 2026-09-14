package io.casehub.qhorus.signing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.util.Base64URL;
import io.casehub.platform.api.signing.SigningProvider;
import io.casehub.qhorus.api.spi.AgentCardSigner;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.X509EncodedKeySpec;

@ApplicationScoped
public class JwsAgentCardSigner implements AgentCardSigner {

    private final OctetKeyPair publicJwk;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;
    private final ObjectMapper mapper;
    private final SigningProvider signingProvider;
    private final String actorId;

    @Inject
    public JwsAgentCardSigner(SigningConfig config, ObjectMapper mapper,
                               SigningProvider signingProvider) {
        this.keyId = config.keyId();
        this.mapper = mapper;
        this.signingProvider = signingProvider;
        this.actorId = config.actorId();
        var material = signingProvider.keyMaterial(config.actorId());
        if (material.isPresent()) {
            byte[] pubBytes = material.get().publicKey();
            try {
                KeyFactory kf = KeyFactory.getInstance("Ed25519");
                this.publicKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
                this.publicJwk = buildJwk((EdECPublicKey) this.publicKey, keyId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load signing key material", e);
            }
        } else {
            this.publicKey = null;
            this.publicJwk = null;
        }
        this.privateKey = null;
    }

    public JwsAgentCardSigner(KeyPair keyPair, String keyId, ObjectMapper mapper) {
        this.keyId = keyId;
        this.mapper = mapper;
        this.signingProvider = null;
        this.actorId = null;
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        this.publicJwk = buildJwk((EdECPublicKey) keyPair.getPublic(), keyId);
    }

    @Override
    public String sign(String cardJson) {
        try {
            ObjectNode cardNode = (ObjectNode) mapper.readTree(cardJson);
            byte[] canonical = JcsCanonicalizer.canonicalize(cardNode);

            byte[] signatureBytes;
            if (signingProvider != null) {
                var result = signingProvider.sign(actorId, canonical);
                if (result.isEmpty()) return cardJson;
                signatureBytes = result.get().signature();
            } else {
                Signature sig = Signature.getInstance("Ed25519");
                sig.initSign(privateKey);
                sig.update(canonical);
                signatureBytes = sig.sign();
            }

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                    .type(new JOSEObjectType("agentcard+jws"))
                    .keyID(keyId)
                    .build();

            ObjectNode result = cardNode.deepCopy();
            ArrayNode signatures = mapper.createArrayNode();
            ObjectNode sigNode = mapper.createObjectNode();
            sigNode.put("protected", header.toBase64URL().toString());
            sigNode.put("signature", Base64URL.encode(signatureBytes).toString());
            signatures.add(sigNode);
            result.set("signatures", signatures);
            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign agent card", e);
        }
    }

    @Override
    public VerificationResult verify(String signedCardJson) {
        try {
            ObjectNode signedNode = (ObjectNode) mapper.readTree(signedCardJson);

            if (!signedNode.has("signatures") || signedNode.get("signatures").isEmpty()) {
                return new VerificationResult(false, null, "No signatures found");
            }

            var sigEntry = signedNode.get("signatures").get(0);
            String protectedB64 = sigEntry.get("protected").asText();
            String signatureB64 = sigEntry.get("signature").asText();

            ObjectNode cardWithoutSigs = signedNode.deepCopy();
            cardWithoutSigs.remove("signatures");
            byte[] canonical = JcsCanonicalizer.canonicalize(cardWithoutSigs);

            JWSHeader header = JWSHeader.parse(Base64URL.from(protectedB64));
            String kid = header.getKeyID();

            if (publicKey == null || !keyId.equals(kid)) {
                return new VerificationResult(false, kid, "kid not found: " + kid);
            }

            byte[] signatureBytes = Base64URL.from(signatureB64).decode();
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(canonical);
            boolean valid = sig.verify(signatureBytes);
            return new VerificationResult(valid, kid, valid ? null : "Signature verification failed");
        } catch (Exception e) {
            return new VerificationResult(false, null, "Verification error: " + e.getMessage());
        }
    }

    @Override
    public String jwks() {
        if (publicJwk == null) {
            return "{\"keys\":[]}";
        }
        try {
            JWKSet jwkSet = new JWKSet(publicJwk);
            return mapper.writeValueAsString(jwkSet.toJSONObject());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JWKS", e);
        }
    }

    private static OctetKeyPair buildJwk(EdECPublicKey pub, String kid) {
        byte[] encoded = pub.getEncoded();
        byte[] rawPub = new byte[32];
        System.arraycopy(encoded, encoded.length - 32, rawPub, 0, 32);
        return new OctetKeyPair.Builder(Curve.Ed25519, Base64URL.encode(rawPub))
                .keyID(kid)
                .keyUse(KeyUse.SIGNATURE)
                .build();
    }
}
