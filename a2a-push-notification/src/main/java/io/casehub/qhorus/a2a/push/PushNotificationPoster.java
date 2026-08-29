package io.casehub.qhorus.a2a.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.casehub.platform.api.credentials.CredentialResolver;
import io.casehub.qhorus.api.a2a.PushNotificationConfig;

@ApplicationScoped
public class PushNotificationPoster {

    private static final Logger LOG = Logger.getLogger(PushNotificationPoster.class);

    private final ObjectMapper mapper;
    private final CredentialResolver credentialResolver;
    private final HttpPoster httpPoster;

    @FunctionalInterface
    interface HttpPoster {
        PushPostResult post(String url, String body, String authHeader);
    }

    @Inject
    public PushNotificationPoster(ObjectMapper mapper, CredentialResolver credentialResolver) {
        this.mapper = mapper;
        this.credentialResolver = credentialResolver;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(5000))
                .build();
        this.httpPoster = (url, body, authHeader) -> {
            try {
                var builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(5000))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                if (authHeader != null) {
                    builder.header("Authorization", authHeader);
                }
                var response = client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return PushPostResult.ok(response.statusCode());
                }
                return PushPostResult.fail(response.statusCode(), "HTTP " + response.statusCode());
            } catch (Exception e) {
                return PushPostResult.fail(0, e.getMessage());
            }
        };
    }

    PushNotificationPoster(ObjectMapper mapper, CredentialResolver credentialResolver, HttpPoster httpPoster) {
        this.mapper = mapper;
        this.credentialResolver = credentialResolver;
        this.httpPoster = httpPoster;
    }

    PushPostResult push(PushNotificationConfig config, String taskState, String messageContent, UUID channelId) {
        String payload = buildPayload(config, taskState, messageContent, channelId);
        String authHeader = resolveAuth(config);
        return httpPoster.post(config.url(), payload, authHeader);
    }

    String buildPayload(PushNotificationConfig config, String taskState, String messageContent, UUID channelId) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("jsonrpc", "2.0");
            root.put("method", "tasks/pushNotification");

            ObjectNode params = root.putObject("params");
            params.put("id", config.id().toString());
            if (config.token() != null) {
                params.put("token", config.token());
            }

            ObjectNode task = params.putObject("task");
            task.put("id", config.taskId());
            task.put("contextId", channelId.toString());

            ObjectNode status = task.putObject("status");
            status.put("state", taskState);
            if (messageContent != null) {
                status.put("message", messageContent);
            }

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            LOG.warnf("Failed to build push payload for config %s: %s", config.id(), e.getMessage());
            return "{}";
        }
    }

    private String resolveAuth(PushNotificationConfig config) {
        if (config.authCredentialsRef() == null) {
            return null;
        }
        try {
            Map<String, String> credentials = credentialResolver.resolve(config.authCredentialsRef());
            if (credentials == null || credentials.isEmpty()) {
                return null;
            }
            String token = credentials.get("token");
            if (token == null) {
                return null;
            }
            String scheme = config.authScheme() != null
                    ? config.authScheme()
                    : credentials.getOrDefault("type", "Bearer");
            return scheme + " " + token;
        } catch (Exception e) {
            LOG.warnf("Failed to resolve credentials for push config %s: %s",
                    config.id(), e.getMessage());
            return null;
        }
    }
}
