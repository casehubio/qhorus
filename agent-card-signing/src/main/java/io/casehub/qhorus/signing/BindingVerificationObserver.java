package io.casehub.qhorus.signing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.qhorus.api.event.BindingVerificationRequestedEvent;
import io.casehub.qhorus.api.instance.ExternalAgentBinding;
import io.casehub.qhorus.api.instance.VerificationStatus;
import io.casehub.qhorus.api.spi.AgentCardSigner;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import jakarta.annotation.PostConstruct;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class BindingVerificationObserver {

    private static final Logger LOG = Logger.getLogger(BindingVerificationObserver.class);

    @Inject
    AgentCardSigner signer;

    @Inject
    ExternalAgentBindingStore store;

    @Inject
    ObjectMapper mapper;

    @Inject
    SigningConfig config;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.jwksCache().connectTimeoutMs()))
                .build();
    }

    void onVerificationRequested(@ObservesAsync BindingVerificationRequestedEvent event) {
        verify(event.binding());
    }

    public void verify(ExternalAgentBinding binding) {
        try {
            String agentCardUrl = binding.endpoint() + "/.well-known/agent.json";
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder().uri(URI.create(agentCardUrl))
                            .timeout(Duration.ofMillis(config.jwksCache().readTimeoutMs()))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                updateBinding(binding, VerificationStatus.UNVERIFIED, null, null);
                LOG.infof("Agent card fetch returned %d for %s", response.statusCode(), binding.endpoint());
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
                LOG.warnf("Agent card verification failed for %s: %s", binding.endpoint(), result.error());
            }
        } catch (Exception e) {
            LOG.infof("Could not fetch agent card for verification: %s — %s", binding.endpoint(), e.getMessage());
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
