package io.casehub.qhorus.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;

class WebhookMessageObserverTest {

    record PostRecord(String url, String body, String secret, Map<String, String> headers) {}

    private WebhookRegistry registry;
    private WebhookMessageObserver observer;
    private final List<PostRecord> posts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        registry = new WebhookRegistry();
        observer = new WebhookMessageObserver(mapper, registry,
                (url, body, secret, headers) -> posts.add(new PostRecord(url, body, secret, headers)));
    }

    @Test
    void scopeIsCluster() {
        assertThat(observer.scope()).isEqualTo(MessageObserver.Scope.CLUSTER);
    }

    @Test
    void postsToRegisteredWebhook() {
        UUID channelId = UUID.randomUUID();
        registry.register(channelId, "https://example.com/hook", null, Map.of());

        observer.onMessage(new MessageReceivedEvent(
                "test-channel", channelId, "t1",
                MessageType.STATUS, "agent-1", null,
                Instant.now(), "hello", null));

        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).url()).isEqualTo("https://example.com/hook");
        assertThat(posts.get(0).body()).isNotEmpty();
    }

    @Test
    void postsToGlobalWebhookForAnyChannel() {
        registry.register(null, "https://example.com/global", null, Map.of());

        UUID channelId = UUID.randomUUID();
        observer.onMessage(new MessageReceivedEvent(
                "test-channel", channelId, "t1",
                MessageType.QUERY, "agent-1", null,
                Instant.now(), "q", null));

        assertThat(posts).hasSize(1);
    }

    @Test
    void passesSecretToPoster() {
        UUID channelId = UUID.randomUUID();
        registry.register(channelId, "https://example.com/hook", "my-secret", Map.of());

        observer.onMessage(new MessageReceivedEvent(
                "test-channel", channelId, "t1",
                MessageType.STATUS, "agent-1", null,
                Instant.now(), "hello", null));

        assertThat(posts.get(0).secret()).isEqualTo("my-secret");
    }

    @Test
    void noPostWhenNoRegistrations() {
        UUID channelId = UUID.randomUUID();
        observer.onMessage(new MessageReceivedEvent(
                "test-channel", channelId, "t1",
                MessageType.STATUS, "agent-1", null,
                Instant.now(), "hello", null));

        assertThat(posts).isEmpty();
    }

    @Test
    void hmacSha256ProducesDeterministicOutput() {
        String sig1 = WebhookMessageObserver.hmacSha256("secret", "data");
        String sig2 = WebhookMessageObserver.hmacSha256("secret", "data");
        assertThat(sig1).isEqualTo(sig2);
        assertThat(sig1).hasSize(64);
    }

    @Test
    void hmacSha256DifferentSecretProducesDifferentHash() {
        String sig1 = WebhookMessageObserver.hmacSha256("secret1", "data");
        String sig2 = WebhookMessageObserver.hmacSha256("secret2", "data");
        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    void postsToMultipleHooks() {
        UUID channelId = UUID.randomUUID();
        registry.register(channelId, "https://a.com/hook", null, Map.of());
        registry.register(channelId, "https://b.com/hook", null, Map.of());
        registry.register(null, "https://global.com/hook", null, Map.of());

        observer.onMessage(new MessageReceivedEvent(
                "test-channel", channelId, "t1",
                MessageType.DONE, "agent-1", null,
                Instant.now(), "done", null));

        assertThat(posts).hasSize(3);
    }
}
