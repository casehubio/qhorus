package io.casehub.qhorus.a2a.outbound;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.qhorus.api.instance.ExternalAgentBinding;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;

@Path("/a2a-outbound/bindings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ExternalAgentBindingResource {

    private final ExternalAgentBindingStore store;

    @Inject
    public ExternalAgentBindingResource(ExternalAgentBindingStore store) {
        this.store = store;
    }

    @PUT
    @Path("/{instanceId}")
    public ExternalAgentBinding put(@PathParam("instanceId") String instanceId,
                                    ExternalAgentBindingRequest req) {
        if (req.endpoint() == null || req.endpoint().isBlank()) {
            throw new jakarta.ws.rs.BadRequestException("endpoint is required");
        }
        String version = req.protocolVersion() != null ? req.protocolVersion() : "1.0";

        ExternalAgentBinding existing = store.findByInstanceId(instanceId).orElse(null);
        UUID id = existing != null ? existing.id() : UUID.randomUUID();

        ExternalAgentBinding binding = new ExternalAgentBinding(
                id, instanceId, req.endpoint(), req.authConfigKey(), version, Instant.now());
        store.put(binding);
        return binding;
    }

    @GET
    @Path("/{instanceId}")
    public ExternalAgentBinding get(@PathParam("instanceId") String instanceId) {
        return store.findByInstanceId(instanceId)
                .orElseThrow(NotFoundException::new);
    }

    @GET
    public List<ExternalAgentBinding> list() {
        return store.findAll();
    }

    @DELETE
    @Path("/{instanceId}")
    public Response delete(@PathParam("instanceId") String instanceId) {
        store.deleteByInstanceId(instanceId);
        return Response.noContent().build();
    }
}
