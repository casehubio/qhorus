package io.casehub.qhorus.spring.integration;

import io.casehub.qhorus.runtime.spring.config.DeliveryConfigProperties;
import io.casehub.qhorus.runtime.spring.config.PresenceConfigProperties;
import io.casehub.qhorus.runtime.spring.config.QhorusConfigProperties;
import io.casehub.qhorus.runtime.spring.config.QhorusTracingConfigProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableConfigurationProperties({
        QhorusConfigProperties.class,
        DeliveryConfigProperties.class,
        PresenceConfigProperties.class,
        QhorusTracingConfigProperties.class
})
class SpringBootCompositionTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private QhorusConfigProperties qhorusConfig;

    @Autowired
    private DeliveryConfigProperties deliveryConfig;

    @Autowired
    private PresenceConfigProperties presenceConfig;

    @Autowired
    private QhorusTracingConfigProperties tracingConfig;

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {
    }

    @Test
    void qhorusConfigDefaultsBind() {
        assertThat(qhorusConfig.cleanup().staleInstanceSeconds()).isEqualTo(120);
        assertThat(qhorusConfig.cleanup().dataRetentionDays()).isEqualTo(7);
        assertThat(qhorusConfig.agentCard().name()).isEqualTo("Qhorus Agent Mesh");
        assertThat(qhorusConfig.agentCard().version()).isEqualTo("1.0.0");
        assertThat(qhorusConfig.a2a().enabled()).isFalse();
        assertThat(qhorusConfig.a2a().sse().heartbeatIntervalSeconds()).isEqualTo(15);
        assertThat(qhorusConfig.watchdog().enabled()).isFalse();
        assertThat(qhorusConfig.attestation().doneConfidence()).isEqualTo(0.7);
        assertThat(qhorusConfig.attestation().credibilityMinDataPoints()).isEqualTo(5);
        assertThat(qhorusConfig.commitment().minObligorTrust()).isEqualTo(0.0);
        assertThat(qhorusConfig.summary().enabled()).isTrue();
        assertThat(qhorusConfig.protocol().lookbackSize()).isEqualTo(50);
        assertThat(qhorusConfig.protocol().requestResponse().maxOpenQueries()).isEqualTo(3);
        assertThat(qhorusConfig.protocol().taskCompletion().maxOpenCommands()).isEqualTo(3);
        assertThat(qhorusConfig.protocol().contributionRequired().maxConsecutive()).isEqualTo(2);
        assertThat(qhorusConfig.routing().defaultTrustThreshold()).isEqualTo(0.0);
    }

    @Test
    void deliveryConfigDefaultsBind() {
        assertThat(deliveryConfig.enabled()).isTrue();
        assertThat(deliveryConfig.batchSize()).isEqualTo(100);
        assertThat(deliveryConfig.maxConsecutiveFailures()).isEqualTo(10);
        assertThat(deliveryConfig.reconciliationInterval()).isEqualTo("30s");
        assertThat(deliveryConfig.maxParticipantRetriesPerCycle()).isEqualTo(100);
        assertThat(deliveryConfig.maxParticipantConsecutiveFailures()).isEqualTo(3);
    }

    @Test
    void presenceConfigDefaultsBind() {
        assertThat(presenceConfig.awayTimeout()).hasMinutes(2);
        assertThat(presenceConfig.offlineTimeout()).hasMinutes(10);
        assertThat(presenceConfig.heartbeatInterval()).hasSeconds(30);
    }

    @Test
    void tracingConfigDefaultsBind() {
        assertThat(tracingConfig.enabled()).isTrue();
        assertThat(tracingConfig.dispatch()).isTrue();
        assertThat(tracingConfig.commitments()).isTrue();
        assertThat(tracingConfig.fanOut()).isTrue();
        assertThat(tracingConfig.ledgerWrite()).isTrue();
        assertThat(tracingConfig.delivery()).isTrue();
    }

    @Test
    void autoConfigurationDiscoverable() {
        assertThat(io.casehub.qhorus.runtime.spring.RuntimeAutoConfiguration.class).isNotNull();
    }

    @Test
    void healthCheckReturnsUp() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/actuator/health"))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("UP");
        }
    }
}
