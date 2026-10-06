package io.casehub.qhorus.cluster;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatcher;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.UUID;

@Path("/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InternalMeshResource {

    private final MessageDispatcher messageDispatcher;
    private final ChannelManager channelManager;
    private final HeartbeatService heartbeatService;
    private final ClusterManager clusterManager;

    public InternalMeshResource(MessageDispatcher messageDispatcher,
                                 ChannelManager channelManager,
                                 HeartbeatService heartbeatService,
                                 ClusterManager clusterManager) {
        this.messageDispatcher = messageDispatcher;
        this.channelManager = channelManager;
        this.heartbeatService = heartbeatService;
        this.clusterManager = clusterManager;
    }

    @POST
    @Path("/dispatch")
    public DispatchResult dispatch(InternalDispatchRequest request) {
        return messageDispatcher.dispatch(request.toMessageDispatch());
    }

    @POST
    @Path("/channel")
    public Channel createChannel(io.casehub.qhorus.api.channel.ChannelCreateRequest request) {
        return channelManager.create(request);
    }

    @POST
    @Path("/channel/{id}/delete")
    public long deleteChannel(@PathParam("id") UUID channelId, @QueryParam("force") boolean force) {
        return channelManager.delete(channelId, force);
    }

    @POST
    @Path("/channel/{id}/pause")
    public Channel pauseChannel(@PathParam("id") UUID channelId) {
        return channelManager.pause(channelId);
    }

    @POST
    @Path("/channel/{id}/resume")
    public Channel resumeChannel(@PathParam("id") UUID channelId) {
        return channelManager.resume(channelId);
    }

    @GET
    @Path("/heartbeat")
    public HeartbeatResponse heartbeat() {
        return heartbeatService.buildLocalResponse();
    }

    @POST
    @Path("/leave")
    public void leave(LeaveRequest request) {
        clusterManager.handlePeerDeparture(request.nodeId());
    }
}
