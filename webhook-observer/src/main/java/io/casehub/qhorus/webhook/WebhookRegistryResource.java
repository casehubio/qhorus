package io.casehub.qhorus.webhook;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.HttpMethod;
import io.casehub.platform.api.mcp.RestMethod;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.platform.api.mcp.RestStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@McpDomain(value = "qhorus/webhooks", basePath = "/qhorus/webhooks")
@ApplicationScoped
public class WebhookRegistryResource {

    @Inject
    WebhookRegistry registry;

    public record RegisterRequest(UUID channelId, String url, String secretRef, Map<String, String> headers) {}

    @PlatformMutation("Register webhook")
    @RestStatus(201)
    public WebhookRegistration register(RegisterRequest request,
                                        @ContextParam("tenancyId") String tenancyId) {
        if (request.url() == null || request.url().isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        return registry.register(
                request.channelId(), tenancyId,
                request.url(), request.secretRef(),
                request.headers() != null ? request.headers() : Map.of());
    }

    @PlatformMutation("Deregister webhook")
    @RestMethod(HttpMethod.DELETE)
    @RestPath("/{id}")
    public void deregister(@PathParam UUID id) {
        if (!registry.deregister(id)) {
            throw new jakarta.ws.rs.NotFoundException();
        }
    }

    @PlatformQuery("List webhooks")
    public Collection<WebhookRegistration> list(UUID channelId,
                                                @ContextParam("tenancyId") String tenancyId) {
        if (channelId != null) {
            return registry.findByChannelId(channelId);
        }
        return registry.listAll(tenancyId);
    }
}
