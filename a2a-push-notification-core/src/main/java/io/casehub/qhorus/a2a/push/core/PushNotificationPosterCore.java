package io.casehub.qhorus.a2a.push.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.platform.api.credentials.CredentialResolver;
import io.casehub.qhorus.api.a2a.PushNotificationConfig;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PushNotificationPosterCore {

    private static final Logger LOG = Logger.getLogger(PushNotificationPosterCore.class.getName());

    private final ObjectMapper mapper;
    private final CredentialResolver credentialResolver;
    private final HttpPoster httpPoster;

    public PushNotificationPosterCore(ObjectMapper mapper, CredentialResolver credentialResolver,
                                       HttpPoster httpPoster) {
        this.mapper = mapper;
        this.credentialResolver = credentialResolver;
        this.httpPoster = httpPoster;
    }

    public PushPostResult push(PushNotificationConfig config, String taskState,
                                String messageContent, UUID channelId) {
        String payload = buildPayload(config, taskState, messageContent, channelId);
        String authHeader = resolveAuth(config);
        return httpPoster.post(config.url(), payload, authHeader);
    }

    public String buildPayload(PushNotificationConfig config, String taskState,
                                String messageContent, UUID channelId) {
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
            LOG.log(Level.WARNING, "Failed to build push payload for config {0}: {1}",
                    new Object[]{config.id(), e.getMessage()});
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
            LOG.log(Level.WARNING, "Failed to resolve credentials for push config {0}: {1}",
                    new Object[]{config.id(), e.getMessage()});
            return null;
        }
    }
}
