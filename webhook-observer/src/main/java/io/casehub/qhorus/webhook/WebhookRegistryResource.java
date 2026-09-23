package io.casehub.qhorus.webhook;

import io.casehub.qhorus.webhook.core.WebhookRegistryCore;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.util.Collection;
import java.util.UUID;

@Path("/qhorus/webhooks")
public class WebhookRegistryResource {

    @Inject
    WebhookRegistryCore core;

    @POST
    public Response register(io.casehub.qhorus.webhook.core.RegisterRequest request) {
        WebhookRegistration reg = core.register(request);
        return Response.status(201).entity(reg).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deregister(@PathParam("id") UUID id) {
        return core.deregister(id)
                .map(v -> Response.noContent().build())
                .orElse(Response.status(404).build());
    }

    @GET
    public Collection<WebhookRegistration> list(@QueryParam("channelId") UUID channelId) {
        return core.list(channelId);
    }
}
