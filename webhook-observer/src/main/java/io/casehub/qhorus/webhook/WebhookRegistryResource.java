package io.casehub.qhorus.webhook;

import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Path("/qhorus/webhooks")
public class WebhookRegistryResource {

    @Inject
    WebhookRegistry registry;

    @Inject
    CurrentPrincipal currentPrincipal;

    public record RegisterRequest(UUID channelId, String url, String secretRef, Map<String, String> headers) {}

    @POST
    public Response register(RegisterRequest request) {
        if (request.url() == null || request.url().isBlank()) {
            return Response.status(400).entity("url is required").build();
        }
        WebhookRegistration reg = registry.register(
                request.channelId(), currentPrincipal.tenancyId(),
                request.url(), request.secretRef(),
                request.headers() != null ? request.headers() : Map.of());
        return Response.status(201).entity(reg).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deregister(@PathParam("id") UUID id) {
        if (registry.deregister(id)) {
            return Response.noContent().build();
        }
        return Response.status(404).build();
    }

    @GET
    public Collection<WebhookRegistration> list(@QueryParam("channelId") UUID channelId) {
        if (channelId != null) {
            return registry.findByChannelId(channelId);
        }
        return registry.listAll(currentPrincipal.tenancyId());
    }
}
