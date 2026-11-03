package io.casehub.qhorus.runtime.api;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.FindOrCreateResult;
import io.casehub.qhorus.api.channel.VisibilityManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Path("/api/visibility")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VisibilityResource {

    @Inject VisibilityManager visibilityManager;

    @POST @Path("/direct") @Transactional
    public Response createDirect(DirectChannelRequest req) {
        FindOrCreateResult result = visibilityManager.getOrCreateDirect(req.participantA(), req.participantB());
        return Response.status(result.wasCreated() ? 201 : 200)
                .entity(ChannelResponse.from(result.channel(), 0, null))
                .build();
    }

    @GET @Path("/direct/{participantId}")
    public List<ChannelResponse> findDirect(@PathParam("participantId") String participantId) {
        return visibilityManager.findDirectChannelsFor(participantId).stream()
                .map(ch -> ChannelResponse.from(ch, 0, null))
                .toList();
    }

    @POST @Path("/group") @Transactional
    public Response createGroup(GroupChannelRequest req) {
        Channel ch = visibilityManager.createGroupChannel(
            req.groupId(), req.groupName(), req.memberIds(), req.coordinatorId());
        return Response.status(201).entity(ChannelResponse.from(ch, 0, null)).build();
    }

    @GET @Path("/group/{groupId}")
    public Response findGroup(@PathParam("groupId") UUID groupId) {
        Optional<Channel> ch = visibilityManager.findGroupChannel(groupId);
        return ch.map(c -> Response.ok(ChannelResponse.from(c, 0, null)).build())
                .orElse(Response.status(404).build());
    }

    public record DirectChannelRequest(String participantA, String participantB) {}
    public record GroupChannelRequest(UUID groupId, String groupName,
                                       List<String> memberIds, String coordinatorId) {}
}
