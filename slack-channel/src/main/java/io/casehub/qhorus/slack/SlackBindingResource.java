package io.casehub.qhorus.slack;

import java.util.UUID;

import io.casehub.qhorus.slack.core.SlackBindingCore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Manages Slack bot bindings — associates a Qhorus channel with a Slack channel.
 *
 * <p>No auth annotations — consistent with all other qhorus REST resources.
 * Network isolation is the current security boundary.
 *
 * <p>put() is intentionally NOT @Transactional — see spec Known Limitations.
 * Order of checks: channel-exists → binding-conflict → credential-valid → evict → save → initChannel.
 */
@Path("/slack-channel/bindings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class SlackBindingResource {

    @Inject
    SlackBindingCore core;

    @PUT
    @Path("/{channelId}")
    public Response put(@PathParam("channelId") UUID channelId, SlackBindingRequest req) {
        try {
            return Response.ok(core.put(channelId, req)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/{channelId}")
    public SlackBindingDto get(@PathParam("channelId") UUID channelId) {
        return core.get(channelId);
    }

    @DELETE
    @Path("/{channelId}")
    public Response delete(@PathParam("channelId") UUID channelId) {
        core.delete(channelId);
        return Response.noContent().build();
    }
}
