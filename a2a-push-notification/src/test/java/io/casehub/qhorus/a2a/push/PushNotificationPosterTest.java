package io.casehub.qhorus.a2a.push;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.platform.api.credentials.CredentialResolver;
import io.casehub.qhorus.api.a2a.PushNotificationConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PushNotificationPosterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private CredentialResolver credentialResolver;
    private AtomicReference<String> capturedUrl;
    private AtomicReference<String> capturedBody;
    private AtomicReference<String> capturedAuth;
    private PushPostResult nextResult;
    private PushNotificationPoster poster;

    @BeforeEach
    void setUp() {
        credentialResolver = mock(CredentialResolver.class);
        capturedUrl = new AtomicReference<>();
        capturedBody = new AtomicReference<>();
        capturedAuth = new AtomicReference<>();
        nextResult = PushPostResult.ok(200);
        poster = new PushNotificationPoster(mapper, credentialResolver,
                (url, body, auth) -> {
                    capturedUrl.set(url);
                    capturedBody.set(body);
                    capturedAuth.set(auth);
                    return nextResult;
                });
    }

    private PushNotificationConfig config(String token, String authScheme, String authRef) {
        return new PushNotificationConfig(
                UUID.randomUUID(), "task-1", UUID.randomUUID(),
                "https://push.example.com/cb", token, authScheme, authRef,
                "default", Instant.now(), null);
    }

    @Test
    void push_success_returnsTrue() {
        PushPostResult result = poster.push(config(null, null, null),
                "working", "doing stuff", UUID.randomUUID());
        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
    }

    @Test
    void push_failure_returnsFalse() {
        nextResult = PushPostResult.fail(500, "HTTP 500");
        PushPostResult result = poster.push(config(null, null, null),
                "working", null, UUID.randomUUID());
        assertThat(result.success()).isFalse();
        assertThat(result.statusCode()).isEqualTo(500);
    }

    @Test
    void push_withAuthHeader_includesAuthorization() {
        when(credentialResolver.resolve("my-cred-key"))
                .thenReturn(Map.of("token", "secret123", "type", "bearer"));
        poster.push(config(null, "Bearer", "my-cred-key"),
                "working", null, UUID.randomUUID());
        assertThat(capturedAuth.get()).isEqualTo("Bearer secret123");
    }

    @Test
    void push_authScheme_takesPrecedenceOverCredentialType() {
        when(credentialResolver.resolve("key"))
                .thenReturn(Map.of("token", "tok", "type", "api_key"));
        poster.push(config(null, "Bearer", "key"),
                "working", null, UUID.randomUUID());
        assertThat(capturedAuth.get()).isEqualTo("Bearer tok");
    }

    @Test
    void push_noAuthRef_noAuthHeader() {
        poster.push(config(null, null, null),
                "working", null, UUID.randomUUID());
        assertThat(capturedAuth.get()).isNull();
    }

    @Test
    void push_withToken_includesTokenInPayload() throws Exception {
        UUID channelId = UUID.randomUUID();
        poster.push(config("verify-me", null, null),
                "completed", "all done", channelId);
        JsonNode root = mapper.readTree(capturedBody.get());
        assertThat(root.path("params").path("token").asText()).isEqualTo("verify-me");
    }

    @Test
    void push_payloadFormat_matchesA2ASpec() throws Exception {
        PushNotificationConfig cfg = config(null, null, null);
        UUID channelId = UUID.randomUUID();
        poster.push(cfg, "working", "in progress", channelId);

        JsonNode root = mapper.readTree(capturedBody.get());
        assertThat(root.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(root.path("method").asText()).isEqualTo("tasks/pushNotification");
        assertThat(root.path("params").path("id").asText()).isEqualTo(cfg.id().toString());

        JsonNode task = root.path("params").path("task");
        assertThat(task.path("id").asText()).isEqualTo("task-1");
        assertThat(task.path("contextId").asText()).isEqualTo(channelId.toString());
        assertThat(task.path("status").path("state").asText()).isEqualTo("working");
        assertThat(task.path("status").path("message").asText()).isEqualTo("in progress");
    }

    @Test
    void push_nullContent_omitsMessageField() throws Exception {
        poster.push(config(null, null, null),
                "completed", null, UUID.randomUUID());
        JsonNode root = mapper.readTree(capturedBody.get());
        assertThat(root.path("params").path("task").path("status").has("message")).isFalse();
    }

    @Test
    void push_postsToConfigUrl() {
        PushNotificationConfig cfg = config(null, null, null);
        poster.push(cfg, "working", null, UUID.randomUUID());
        assertThat(capturedUrl.get()).isEqualTo("https://push.example.com/cb");
    }
}
