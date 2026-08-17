package io.casehub.qhorus.runtime.api;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.channel.SpaceCreateRequest;
import io.casehub.qhorus.runtime.channel.SpaceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/api/spaces")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class SpaceResource {

    @Inject SpaceService spaceService;

    @GET
    public List<Space> listRoots() {
        return spaceService.listRoots();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        return spaceService.findById(UUID.fromString(id))
            .map(s -> Response.ok(s).build())
            .orElse(Response.status(404).build());
    }

    @GET
    @Path("/{id}/children")
    public List<Space> children(@PathParam("id") String id) {
        return spaceService.listChildren(UUID.fromString(id));
    }

    @POST
    public Response create(SpaceCreateRequest request) {
        try {
            var space = spaceService.create(request);
            return Response.ok(space).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(400).entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") String id, SpaceUpdateRequest request) {
        var uuid = UUID.fromString(id);
        try {
            if (request.name() != null) {
                spaceService.rename(uuid, request.name());
            }
            if (request.description() != null) {
                spaceService.updateDescription(uuid, request.description());
            }
            if (request.parentSpaceId() != null) {
                spaceService.moveSpace(uuid, request.parentSpaceId());
            }
            return spaceService.findById(uuid)
                .map(s -> Response.ok(s).build())
                .orElse(Response.status(404).build());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(400).entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        try {
            spaceService.delete(UUID.fromString(id));
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(404).entity(new ErrorResponse(e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(409).entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    @GET
    @Path("/{id}/channels")
    public List<Channel> channelsInSpace(@PathParam("id") String id) {
        return spaceService.listChannels(UUID.fromString(id));
    }

    record SpaceUpdateRequest(String name, String description, UUID parentSpaceId) {}
    record ErrorResponse(String error) {}
}
