package io.casehub.qhorus.runtime.api;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.channel.SpaceCreateRequest;
import io.casehub.qhorus.runtime.api.core.SpaceCore;
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

@Path("/api/spaces")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class SpaceResource {

    @Inject SpaceCore core;

    @GET
    public List<Space> listRoots() {
        return core.listRoots();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        return core.get(id)
            .map(s -> Response.ok(s).build())
            .orElse(Response.status(404).build());
    }

    @GET
    @Path("/{id}/children")
    public List<Space> children(@PathParam("id") String id) {
        return core.children(id);
    }

    @POST
    public Response create(SpaceCreateRequest request) {
        try {
            var space = core.create(request);
            return Response.status(Response.Status.CREATED).entity(space).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(400).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") String id, io.casehub.qhorus.runtime.api.core.SpaceUpdateRequest request) {
        try {
            return core.update(id, request)
                .map(s -> Response.ok(s).build())
                .orElse(Response.status(404).build());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(400).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        try {
            core.delete(id);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(404).entity(e.getMessage()).build();
        } catch (IllegalStateException e) {
            return Response.status(409).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/{id}/channels")
    public List<Channel> channelsInSpace(@PathParam("id") String id) {
        return core.channelsInSpace(id);
    }
}
