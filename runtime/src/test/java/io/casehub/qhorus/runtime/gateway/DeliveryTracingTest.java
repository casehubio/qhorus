package io.casehub.qhorus.runtime.gateway;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import io.casehub.qhorus.api.gateway.ChannelBackend;
import io.casehub.qhorus.api.gateway.DeliveryGuarantee;
import io.casehub.qhorus.runtime.config.DeliveryConfig;
import io.casehub.qhorus.runtime.config.QhorusTracingConfig;

class DeliveryTracingTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider provider;
    private UUID channelId;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        channelId = UUID.randomUUID();
    }

    private QhorusTracingConfig tracingConfig(boolean enabled) {
        return new QhorusTracingConfig() {
            @Override public boolean enabled() { return enabled; }
            @Override public boolean dispatch() { return true; }
            @Override public boolean commitments() { return true; }
            @Override public boolean fanOut() { return true; }
            @Override public boolean ledgerWrite() { return true; }
            @Override public boolean delivery() { return true; }
        };
    }

    private io.casehub.qhorus.api.store.CrossTenantChannelStore emptyChannelStore() {
        return new io.casehub.qhorus.api.store.CrossTenantChannelStore() {
            @Override public java.util.Optional<io.casehub.qhorus.api.channel.Channel> findById(UUID id) {
                return java.util.Optional.empty();
            }
            @Override public java.util.Optional<io.casehub.qhorus.api.channel.Channel> findByNameAndTenancy(String name, String tenancyId) {
                return java.util.Optional.empty();
            }
            @Override public java.util.List<io.casehub.qhorus.api.channel.Channel> listAll() {
                return java.util.List.of();
            }
        };
    }

    private DeliveryConfig defaultDeliveryConfig() {
        return new DeliveryConfig() {
            @Override public boolean enabled() { return true; }
            @Override public int batchSize() { return 10; }
            @Override public int maxConsecutiveFailures() { return 3; }
            @Override public String reconciliationInterval() { return "30s"; }
            @Override public int maxParticipantRetriesPerCycle() { return 100; }
            @Override public int maxParticipantConsecutiveFailures() { return 3; }
        };
    }

    private ChannelBackend testBackend() {
        return new ChannelBackend() {
            @Override public String backendId() { return "test-backend"; }
            @Override public io.casehub.platform.api.identity.ActorType actorType() {
                return io.casehub.platform.api.identity.ActorType.SYSTEM;
            }
            @Override public DeliveryGuarantee deliveryGuarantee() { return DeliveryGuarantee.AT_LEAST_ONCE; }
            @Override public void open(io.casehub.qhorus.api.gateway.ChannelRef channel, java.util.Map<String, String> metadata) {}
            @Override public void post(io.casehub.qhorus.api.gateway.ChannelRef channel, io.casehub.qhorus.api.gateway.OutboundMessage message) {}
            @Override public void close(io.casehub.qhorus.api.gateway.ChannelRef channel) {}
        };
    }

    @Test
    void deliverBatch_creates_span_when_tracing_enabled() {
        DeliveryBatchExecutor batchExecutor = new DeliveryBatchExecutor(
                null, emptyChannelStore(), null, defaultDeliveryConfig(),
                null, () -> provider.get("qhorus-test"), tracingConfig(true));

        DeliveryBatchExecutor.BatchResult result = batchExecutor.deliverBatch(
                channelId, testBackend(),
                new DeliveryBatchExecutor.HealthCallback() {
                    @Override public void recordFailure(String backendId) {}
                    @Override public void resetHealth(String backendId) {}
                });

        assertThat(result.status()).isEqualTo(DeliveryBatchExecutor.Status.FAILED);

        java.util.List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("qhorus.delivery.pump");
        assertThat(span.getAttributes().asMap())
                .containsEntry(AttributeKey.stringKey("qhorus.channel.id"), channelId.toString())
                .containsEntry(AttributeKey.stringKey("qhorus.delivery.backend_id"), "test-backend");
        assertThat(span.getParentSpanContext().isValid()).isFalse();
    }

    @Test
    void deliverBatch_disabled_tracing_creates_no_spans() {
        DeliveryBatchExecutor batchExecutor = new DeliveryBatchExecutor(
                null, emptyChannelStore(), null, defaultDeliveryConfig(),
                null, () -> provider.get("qhorus-test"), tracingConfig(false));

        batchExecutor.deliverBatch(channelId, testBackend(),
                new DeliveryBatchExecutor.HealthCallback() {
                    @Override public void recordFailure(String backendId) {}
                    @Override public void resetHealth(String backendId) {}
                });

        assertThat(exporter.getFinishedSpanItems()).isEmpty();
    }
}
