package io.casehub.qhorus.signing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.a2a.model.AgentCapabilities;
import io.casehub.a2a.model.AgentCard;
import io.casehub.qhorus.api.spi.AgentCardSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwsAgentCardSignerTest {

    private JwsAgentCardSigner signer;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        mapper = new ObjectMapper();
        signer = new JwsAgentCardSigner(kp, "test-kid", mapper);
    }

    @Test
    void signAndVerifyRoundTrip() {
        var card = new AgentCard("test-agent", "A test agent",
                "https://agent.example.com", "1.0",
                List.of(), new AgentCapabilities(true, false),
                Map.of(), "default", null);

        String signed = signer.sign(mapper.valueToTree(card).toString());
        assertThat(signed).contains("\"signatures\"");

        ObjectNode signedNode = parseJson(signed);
        assertThat(signedNode.has("signatures")).isTrue();
        assertThat(signedNode.get("signatures").size()).isEqualTo(1);

        AgentCardSigner.VerificationResult result = signer.verify(signed);
        assertThat(result.verified()).isTrue();
        assertThat(result.keyId()).isEqualTo("test-kid");
        assertThat(result.error()).isNull();
    }

    @Test
    void verifyDetectsTamperedPayload() {
        var card = new AgentCard("test-agent", "A test agent",
                "https://agent.example.com", "1.0",
                List.of(), new AgentCapabilities(true, false),
                Map.of(), "default", null);

        String signed = signer.sign(mapper.valueToTree(card).toString());
        ObjectNode signedNode = parseJson(signed);
        signedNode.put("name", "tampered-agent");

        AgentCardSigner.VerificationResult result = signer.verify(signedNode.toString());
        assertThat(result.verified()).isFalse();
    }

    @Test
    void verifyReturnsUnverifiedForNoSignatures() {
        ObjectNode unsigned = mapper.createObjectNode();
        unsigned.put("name", "test-agent");

        AgentCardSigner.VerificationResult result = signer.verify(unsigned.toString());
        assertThat(result.verified()).isFalse();
        assertThat(result.error()).contains("No signatures");
    }

    @Test
    void jwksContainsPublicKey() {
        String jwksJson = signer.jwks();
        ObjectNode jwks = parseJson(jwksJson);
        assertThat(jwks.has("keys")).isTrue();
        assertThat(jwks.get("keys").size()).isEqualTo(1);
        var key = jwks.get("keys").get(0);
        assertThat(key.get("kid").asText()).isEqualTo("test-kid");
        assertThat(key.get("use").asText()).isEqualTo("sig");
        assertThat(key.get("kty").asText()).isEqualTo("OKP");
        assertThat(key.get("crv").asText()).isEqualTo("Ed25519");
    }

    @Test
    void signPreservesAllCardFields() {
        var card = new AgentCard("my-agent", "Description here",
                "https://agent.example.com", "2.0",
                List.of(), new AgentCapabilities(true, true),
                Map.of("schemes", List.of("bearer")), "tenant-1", null);

        String signed = signer.sign(mapper.valueToTree(card).toString());
        ObjectNode signedNode = parseJson(signed);
        assertThat(signedNode.get("name").asText()).isEqualTo("my-agent");
        assertThat(signedNode.get("description").asText()).isEqualTo("Description here");
        assertThat(signedNode.get("version").asText()).isEqualTo("2.0");
        assertThat(signedNode.get("tenancyId").asText()).isEqualTo("tenant-1");
    }

    private ObjectNode parseJson(String json) {
        try {
            return (ObjectNode) mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
