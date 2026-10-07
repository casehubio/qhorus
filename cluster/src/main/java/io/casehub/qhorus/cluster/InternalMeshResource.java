package io.casehub.qhorus.cluster;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.runtime.cdi.CdiMessageService;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.quarkus.arc.properties.IfBuildProperty;
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
@IfBuildProperty(name = "casehub.qhorus.relay.enabled", stringValue = "true",
                 enableIfMissing = false)
public class InternalMeshResource {

    private final CdiMessageService messageService;
    private final ChannelService channelService;
    private final HeartbeatService heartbeatService;
    private final ClusterManager clusterManager;

    public InternalMeshResource(CdiMessageService messageService,
                                 ChannelService channelService,
                                 HeartbeatService heartbeatService,
                                 ClusterManager clusterManager) {
        this.messageService = messageService;
        this.channelService = channelService;
        this.heartbeatService = heartbeatService;
        this.clusterManager = clusterManager;
    }

    @POST
    @Path("/dispatch")
    public DispatchResult dispatch(InternalDispatchRequest request) {
        return messageService.dispatch(request.toMessageDispatch());
    }

    @POST
    @Path("/channel")
    public Channel createChannel(io.casehub.qhorus.api.channel.ChannelCreateRequest request) {
        return channelService.create(request);
    }

    @POST
    @Path("/channel/{id}/delete")
    public long deleteChannel(@PathParam("id") UUID channelId, @QueryParam("force") boolean force) {
        return channelService.delete(channelId, force);
    }

    @POST
    @Path("/channel/{id}/pause")
    public Channel pauseChannel(@PathParam("id") UUID channelId) {
        return channelService.pause(channelId);
    }

    @POST
    @Path("/channel/{id}/resume")
    public Channel resumeChannel(@PathParam("id") UUID channelId) {
        return channelService.resume(channelId);
    }

    @POST
    @Path("/channel/{id}/config")
    public Channel channelConfig(@PathParam("id") UUID channelId, ChannelConfigRequest request) {
        return request.applyTo(channelService, channelId);
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
