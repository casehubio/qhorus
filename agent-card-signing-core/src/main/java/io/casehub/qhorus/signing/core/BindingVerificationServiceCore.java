package io.casehub.qhorus.signing.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.qhorus.api.instance.ExternalAgentBinding;
import io.casehub.qhorus.api.instance.VerificationStatus;
import io.casehub.qhorus.api.spi.AgentCardSigner;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BindingVerificationServiceCore {

    private static final Logger LOG = Logger.getLogger(BindingVerificationServiceCore.class.getName());

    private final AgentCardSigner signer;
    private final ExternalAgentBindingStore store;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final int readTimeoutMs;

    public BindingVerificationServiceCore(AgentCardSigner signer,
                                           ExternalAgentBindingStore store,
                                           ObjectMapper mapper,
                                           int connectTimeoutMs,
                                           int readTimeoutMs) {
        this.signer = signer;
        this.store = store;
        this.mapper = mapper;
        this.readTimeoutMs = readTimeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    public void verify(ExternalAgentBinding binding) {
        try {
            String agentCardUrl = binding.endpoint() + "/.well-known/agent.json";
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder().uri(URI.create(agentCardUrl))
                            .timeout(Duration.ofMillis(readTimeoutMs))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                updateBinding(binding, VerificationStatus.UNVERIFIED, null, null);
                LOG.log(Level.INFO, "Agent card fetch returned {0} for {1}",
                        new Object[]{response.statusCode(), binding.endpoint()});
                return;
            }

            ObjectNode cardJson = (ObjectNode) mapper.readTree(response.body());
            if (!cardJson.has("signatures") || cardJson.get("signatures").isEmpty()) {
                updateBinding(binding, VerificationStatus.UNVERIFIED, null, null);
                return;
            }

            AgentCardSigner.VerificationResult result = signer.verify(response.body());
            if (result.verified()) {
                updateBinding(binding, VerificationStatus.VERIFIED, Instant.now(), result.keyId());
            } else {
                updateBinding(binding, VerificationStatus.FAILED, null, null);
                LOG.log(Level.WARNING, "Agent card verification failed for {0}: {1}",
                        new Object[]{binding.endpoint(), result.error()});
            }
        } catch (Exception e) {
            LOG.log(Level.INFO, "Could not fetch agent card for verification: {0} — {1}",
                    new Object[]{binding.endpoint(), e.getMessage()});
            updateBinding(binding, VerificationStatus.UNVERIFIED, null, null);
        }
    }

    private void updateBinding(ExternalAgentBinding original, VerificationStatus status,
                               Instant verifiedAt, String keyId) {
        var updated = new ExternalAgentBinding(
                original.id(), original.instanceId(), original.endpoint(),
                original.authConfigKey(), original.protocolVersion(), original.createdAt(),
                status, verifiedAt, keyId);
        store.put(updated);
    }
}
