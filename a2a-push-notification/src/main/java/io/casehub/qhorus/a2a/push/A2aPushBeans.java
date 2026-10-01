package io.casehub.qhorus.a2a.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.credentials.CredentialResolver;
import io.casehub.qhorus.a2a.push.core.HttpPoster;
import io.casehub.qhorus.a2a.push.core.PushNotificationBackendCore;
import io.casehub.qhorus.a2a.push.core.PushNotificationCleanupJobCore;
import io.casehub.qhorus.a2a.push.core.PushNotificationPosterCore;
import io.casehub.qhorus.a2a.push.core.PushPostResult;
import io.casehub.qhorus.api.gateway.BackendRegistry;
import io.casehub.qhorus.api.store.CrossTenantPushNotificationConfigStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;

@ApplicationScoped
public class A2aPushBeans {

    @Produces
    @ApplicationScoped
    public PushNotificationPosterCore poster(ObjectMapper mapper, CredentialResolver credentialResolver,
                                              PushConfig config) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.httpTimeoutMs()))
                .build();
        HttpPoster httpPoster = (url, body, authHeader) -> {
            try {
                var builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(config.httpTimeoutMs()))
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
        return new PushNotificationPosterCore(mapper, credentialResolver, httpPoster);
    }

    @Produces
    @ApplicationScoped
    public PushNotificationBackendCore backend(CrossTenantPushNotificationConfigStore store,
                                                PushNotificationPosterCore poster,
                                                BackendRegistry backendRegistry,
                                                PushConfig config) {
        return new PushNotificationBackendCore(store, poster, backendRegistry, config.maxUrlFailures());
    }

    @Produces
    @ApplicationScoped
    public PushNotificationCleanupJobCore cleanupJob(CrossTenantPushNotificationConfigStore store,
                                                      PushConfig config) {
        return new PushNotificationCleanupJobCore(store, config.ttlThreshold(), config.enabled(),
                Clock.systemUTC());
    }
}
