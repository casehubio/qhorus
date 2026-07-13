package io.casehub.qhorus.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
@ApplicationScoped
public class WebhookMessageObserver implements MessageObserver {

    private static final Logger LOG = Logger.getLogger(WebhookMessageObserver.class);

    private final ObjectMapper objectMapper;
    private final WebhookRegistry registry;
    private final WebhookPoster poster;

    @FunctionalInterface
    interface WebhookPoster {
        void post(String url, String body, String secret, Map<String, String> headers);
    }

    @Inject
    public WebhookMessageObserver(ObjectMapper objectMapper, WebhookRegistry registry,
                                   WebhookObserverConfig config) {
        this.objectMapper = objectMapper;
        this.registry = registry;
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.timeoutMs()))
                .build();
        final int timeoutMs = config.timeoutMs();
        this.poster = (url, body, secret, headers) -> {
            Thread.ofVirtual().start(() -> {
                try {
                    var builder = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofMillis(timeoutMs))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body));
                    headers.forEach(builder::header);
                    if (secret != null) {
                        builder.header("X-Qhorus-Signature", hmacSha256(secret, body));
                    }
                    var response = httpClient.send(builder.build(),
                            HttpResponse.BodyHandlers.discarding());
                    LOG.debugf("Webhook POST %s -> %d", url, response.statusCode());
                } catch (Exception e) {
                    LOG.warnf("Webhook POST %s failed: %s", url, e.getMessage());
                }
            });
        };
    }

    WebhookMessageObserver(ObjectMapper objectMapper, WebhookRegistry registry, WebhookPoster poster) {
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.poster = poster;
    }

    @Override
    public void onMessage(MessageReceivedEvent event) {
        Set<WebhookRegistration> hooks = registry.findForChannel(event.channelId());
        if (hooks.isEmpty()) {
            return;
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            LOG.warnf("Failed to serialize event for webhook — channel=%s: %s",
                    event.channelId(), e.getMessage());
            return;
        }

        for (WebhookRegistration hook : hooks) {
            poster.post(hook.url(), body, hook.secret(), hook.headers());
        }
    }

    @Override
    public Scope scope() {
        return Scope.CLUSTER;
    }

    static String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 computation failed", e);
        }
    }
}
