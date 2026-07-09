package io.casehub.qhorus.runtime.message;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.ChannelStore;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@TestProfile(DispatchTracingTest.Profile.class)
class DispatchTracingTest {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.of(
                    "casehub.qhorus.tracing.enabled", "true",
                    "casehub.qhorus.tracing.dispatch", "true"
            );
        }
    }

    @ApplicationScoped
    static class TestTracerProducer {
        static final InMemorySpanExporter EXPORTER = InMemorySpanExporter.create();
        static final SdkTracerProvider PROVIDER = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(EXPORTER))
                .build();

        @Produces
        @ApplicationScoped
        Tracer tracer() {
            return PROVIDER.get("qhorus-test");
        }
    }

    @Inject
    MessageService messageService;

    @Inject
    ChannelStore channelStore;

    @BeforeEach
    void setUp() {
        TestTracerProducer.EXPORTER.reset();
    }

    @Transactional
    UUID createChannel(String name) {
        Channel channel = Channel.builder(name)
                .semantic(ChannelSemantic.APPEND)
                .allowedWriters(List.of())
                .adminInstances(List.of())
                .build();
        return channelStore.put(channel).id();
    }

    @Test
    void dispatch_creates_span_with_message_attributes() {
        UUID channelId = createChannel("trace-test-1");

        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            messageService.dispatch(MessageDispatch.builder()
                    .channelId(channelId)
                    .sender("test-sender")
                    .type(MessageType.STATUS)
                    .content("test content")
                    .actorType(ActorType.AGENT)
                    .build());
        });

        var spans = TestTracerProducer.EXPORTER.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("qhorus.dispatch"))
                .toList();
        assertThat(spans).hasSize(1);

        var span = spans.get(0);
        assertThat(span.getAttributes().asMap())
                .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("qhorus.message.type"), "STATUS")
                .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("qhorus.message.sender"), "test-sender")
                .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("qhorus.actor.type"), "AGENT")
                .containsKey(io.opentelemetry.api.common.AttributeKey.stringKey("qhorus.channel.id"))
                .containsKey(io.opentelemetry.api.common.AttributeKey.stringKey("qhorus.channel.name"));
    }

    @Test
    void dispatch_with_correlation_id_adds_correlation_attribute() {
        UUID channelId = createChannel("trace-test-2");

        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            messageService.dispatch(MessageDispatch.builder()
                    .channelId(channelId)
                    .sender("test-sender")
                    .type(MessageType.QUERY)
                    .content("query content")
                    .correlationId("test-corr-123")
                    .actorType(ActorType.AGENT)
                    .build());
        });

        var spans = TestTracerProducer.EXPORTER.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("qhorus.dispatch"))
                .toList();
        assertThat(spans).hasSize(1);

        var span = spans.get(0);
        assertThat(span.getAttributes().asMap())
                .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("qhorus.message.correlation_id"), "test-corr-123");
    }

    @Test
    void dispatch_with_target_adds_target_attribute() {
        UUID channelId = createChannel("trace-test-3");

        // Create initial command message to reply to
        Long messageId = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            return messageService.dispatch(MessageDispatch.builder()
                    .channelId(channelId)
                    .sender("requester")
                    .type(MessageType.COMMAND)
                    .content("initial command")
                    .correlationId("test-corr-456")
                    .actorType(ActorType.AGENT)
                    .build()).messageId();
        });

        TestTracerProducer.EXPORTER.reset();

        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            messageService.dispatch(MessageDispatch.builder()
                    .channelId(channelId)
                    .sender("test-sender")
                    .type(MessageType.HANDOFF)
                    .content("handoff content")
                    .correlationId("test-corr-456")
                    .inReplyTo(messageId)
                    .target("role:specialist")
                    .actorType(ActorType.AGENT)
                    .build());
        });

        var spans = TestTracerProducer.EXPORTER.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("qhorus.dispatch"))
                .toList();
        assertThat(spans).hasSize(1);

        var span = spans.get(0);
        assertThat(span.getAttributes().asMap())
                .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("qhorus.message.target"), "role:specialist");
    }

    @Test
    void dispatch_records_error_on_exception() {
        // Create channel with ACL that blocks "unauthorized-sender"
        UUID channelId = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            Channel channel = Channel.builder("trace-test-acl")
                    .semantic(ChannelSemantic.APPEND)
                    .allowedWriters(List.of("authorized-sender"))  // Only allow specific sender
                    .adminInstances(List.of())
                    .build();
            return channelStore.put(channel).id();
        });

        assertThatThrownBy(() -> {
            io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
                messageService.dispatch(MessageDispatch.builder()
                        .channelId(channelId)
                        .sender("unauthorized-sender")  // This sender is not in allowed_writers
                        .type(MessageType.STATUS)
                        .content("test")
                        .actorType(ActorType.AGENT)
                        .build());
            });
        }).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("not permitted");

        var spans = TestTracerProducer.EXPORTER.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        var span = spans.get(0);
        assertThat(span.getName()).isEqualTo("qhorus.dispatch");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
        assertThat(span.getEvents()).anySatisfy(event -> {
            assertThat(event.getName()).isEqualTo("exception");
        });
    }
}
