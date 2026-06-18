package io.casehub.qhorus.slack;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
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

import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Manages Slack bot bindings — associates a Qhorus channel with a Slack channel.
 *
 * <p>No auth annotations — consistent with all other qhorus REST resources.
 * Network isolation is the current security boundary.
 */
@Path("/slack-channel/bindings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class SlackBindingResource {

    private final SlackBotBindingStore bindingStore;
    private final ChannelService channelService;
    private final ChannelGateway gateway;
    private final SlackChannelBackend backend;

    public SlackBindingResource(SlackBotBindingStore bindingStore,
                                ChannelService channelService,
                                ChannelGateway gateway,
                                SlackChannelBackend backend) {
        this.bindingStore = bindingStore;
        this.channelService = channelService;
        this.gateway = gateway;
        this.backend = backend;
    }

    /** Creates or replaces a Slack binding. Returns HTTP 400 if the workspaceId credential is not configured. */
    @PUT
    @Path("/{channelId}")
    public Response put(@PathParam("channelId") UUID channelId, SlackBindingRequest req) {
        String credKey = "casehub.qhorus.slack-channel.credentials." + req.workspaceId();
        try {
            ConfigProvider.getConfig().getValue(credKey, String.class);
        } catch (NoSuchElementException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing credential: " + credKey)
                    .build();
        }

        SlackBotBinding binding = new SlackBotBinding();
        binding.channelId = channelId;
        binding.slackChannelId = req.slackChannelId();
        binding.workspaceId = req.workspaceId();
        binding.createdAt = Instant.now();
        bindingStore.save(binding);

        // Fire ChannelInitialisedEvent so the backend self-registers without a restart
        channelService.findById(channelId).ifPresent(ch ->
                gateway.initChannel(channelId, new ChannelRef(channelId, ch.name)));

        return Response.ok(SlackBindingDto.from(channelId, binding)).build();
    }

    /** Returns the binding for the given channel. Token is never included. */
    @GET
    @Path("/{channelId}")
    public SlackBindingDto get(@PathParam("channelId") UUID channelId) {
        return bindingStore.findByChannelId(channelId)
                .map(b -> SlackBindingDto.from(channelId, b))
                .orElseThrow(NotFoundException::new);
    }

    /**
     * Removes the binding and evicts in-memory state. DB thread cache rows are preserved —
     * TTL cleanup handles them. In-flight commitments may still complete.
     */
    @DELETE
    @Path("/{channelId}")
    public Response delete(@PathParam("channelId") UUID channelId) {
        // Read binding from cache BEFORE DB delete to access slackChannelId
        SlackBotBinding binding = backend.bindingCache.get(channelId);
        bindingStore.deleteByChannelId(channelId);
        gateway.deregisterBackend(channelId, SlackChannelBackend.BACKEND_ID);
        backend.bindingCache.remove(channelId);
        if (binding != null) backend.slackToChannel.remove(binding.slackChannelId);
        backend.threadCache.remove(channelId);
        // DB thread cache rows intentionally NOT deleted — TTL cleanup handles them
        return Response.noContent().build();
    }
}
