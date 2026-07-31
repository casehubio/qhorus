package io.casehub.qhorus.runtime.api;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.SpaceStore;
import io.casehub.qhorus.api.store.query.ChannelQuery;
import io.casehub.qhorus.runtime.channel.ChannelService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/channels")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChannelResource {

    @Inject
    ChannelService channelService;

    @Inject
    MessageStore messageStore;

    @Inject
    SpaceStore spaceStore;

    @POST
    public Response create(final CreateChannelRequest req) {
        try {
            final var builder = ChannelCreateRequest.builder(req.name());
            if (req.description() != null) builder.description(req.description());
            if (req.semantic() != null) builder.semantic(ChannelSemantic.valueOf(req.semantic()));
            if (req.barrierContributors() != null) builder.barrierContributors(req.barrierContributors());
            if (req.allowedWriters() != null) builder.allowedWriters(req.allowedWriters());
            if (req.adminInstances() != null) builder.adminInstances(req.adminInstances());
            if (req.reviewerInstances() != null) builder.reviewerInstances(req.reviewerInstances());
            if (req.allowedTypes() != null) builder.allowedTypes(parseTypes(req.allowedTypes()));
            if (req.deniedTypes() != null) builder.deniedTypes(parseTypes(req.deniedTypes()));
            if (req.protocols() != null) builder.protocols(req.protocols());
            if (req.protocolParticipants() != null) builder.protocolParticipants(req.protocolParticipants());
            if (req.spaceId() != null) builder.spaceId(req.spaceId());
            if (req.trackDelivery() != null) builder.trackDelivery(req.trackDelivery());
            if (req.rateLimitPerChannel() != null) builder.rateLimitPerChannel(req.rateLimitPerChannel());
            if (req.rateLimitPerInstance() != null) builder.rateLimitPerInstance(req.rateLimitPerInstance());

            final Channel ch = channelService.create(builder.build());
            return Response.status(Response.Status.CREATED)
                    .entity(toResponse(ch))
                    .build();
        } catch (IllegalArgumentException e) {
            return error(400, e.getMessage());
        }
    }

    @GET
    public List<ChannelResponse> list(
            @QueryParam("prefix") final String prefix,
            @QueryParam("spaceId") final UUID spaceId,
            @QueryParam("paused") final Boolean paused) {

        final List<Channel> channels;
        if (prefix != null || spaceId != null || paused != null) {
            final var qb = ChannelQuery.builder();
            if (prefix != null) qb.namePrefix(prefix);
            if (spaceId != null) qb.spaceId(spaceId);
            if (paused != null) qb.paused(paused);
            channels = channelService.scan(qb.build());
        } else {
            channels = channelService.listAll();
        }
        return toResponseList(channels);
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") final String id) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        return Response.ok(toResponse(ch)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") final String id,
                           @QueryParam("force") @DefaultValue("false") final boolean force) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        try {
            channelService.delete(ch.id(), force);
            return Response.noContent().build();
        } catch (IllegalStateException e) {
            return error(409, e.getMessage());
        }
    }

    // -- Resolution ---------------------------------------------------------


    // -- Lifecycle ----------------------------------------------------------


    @POST
    @Path("/{id}/pause")
    @Consumes(MediaType.WILDCARD)
    public Response pause(@PathParam("id") final String id) {
        final Channel ch = resolve(id);
        if (ch == null) {return error(404, "Channel not found: " + id);}
        final Channel updated = channelService.pause(ch.id());
        return Response.ok(toResponse(updated)).build();
    }


    @POST
    @Path("/{id}/resume")
    @Consumes(MediaType.WILDCARD)
    public Response resume(@PathParam("id") final String id) {
        final Channel ch = resolve(id);
        if (ch == null) {return error(404, "Channel not found: " + id);}
        final Channel updated = channelService.resume(ch.id());
        return Response.ok(toResponse(updated)).build();
    }


    // -- Settings -----------------------------------------------------------

    @PUT
    @Path("/{id}/allowed-writers")
    public Response setAllowedWriters(@PathParam("id") final String id, final StringListRequest req) {
        final Channel ch = resolve(id);
        if (ch == null) {return error(404, "Channel not found: " + id);}
        final Channel updated = channelService.setAllowedWriters(ch.id(), req.values() != null ? req.values() : List.of());
        return Response.ok(toResponse(updated)).build();
    }

    @PUT
    @Path("/{id}/admin-instances")
    public Response setAdminInstances(@PathParam("id") final String id, final StringListRequest req) {
        final Channel ch = resolve(id);
        if (ch == null) {return error(404, "Channel not found: " + id);}
        final Channel updated = channelService.setAdminInstances(ch.id(), req.values() != null ? req.values() : List.of());
        return Response.ok(toResponse(updated)).build();
    }

    @PUT
    @Path("/{id}/reviewer-instances")
    public Response setReviewerInstances(@PathParam("id") final String id, final StringListRequest req) {
        final Channel ch = resolve(id);
        if (ch == null) {return error(404, "Channel not found: " + id);}
        final Channel updated = channelService.setReviewerInstances(ch.id(), req.values() != null ? req.values() : List.of());
        return Response.ok(toResponse(updated)).build();
    }

    @PUT
    @Path("/{id}/type-constraints")
    public Response setTypeConstraints(@PathParam("id") final String id, final TypeConstraintsRequest req) {
        final Channel ch = resolve(id);
        if (ch == null) {return error(404, "Channel not found: " + id);}
        try {
            final Set<MessageType> allowed = req.allowedTypes() != null ? parseTypes(req.allowedTypes()) : null;
            final Set<MessageType> denied  = req.deniedTypes() != null ? parseTypes(req.deniedTypes()) : null;
            final Channel          updated = channelService.setTypeConstraints(ch.id(), allowed, denied);
            return Response.ok(toResponse(updated)).build();
        } catch (IllegalArgumentException e) {
            return error(400, e.getMessage());
        }
    }

    @PUT
    @Path("/{id}/rate-limits")
    public Response setRateLimits(@PathParam("id") final String id, final RateLimitsRequest req) {
        final Channel ch = resolve(id);
        if (ch == null) {return error(404, "Channel not found: " + id);}
        final Channel updated = channelService.setRateLimits(ch.id(), req.perChannel(), req.perInstance());
        return Response.ok(toResponse(updated)).build();
    }

    @PUT
    @Path("/{id}/protocols")
    public Response setProtocols(@PathParam("id") final String id, final StringListRequest req) {
        final Channel ch = resolve(id);
        if (ch == null) {return error(404, "Channel not found: " + id);}
        try {
            final Channel updated = channelService.setProtocols(ch.id(), req.values() != null ? req.values() : List.of());
            return Response.ok(toResponse(updated)).build();
        } catch (IllegalArgumentException e) {
            return error(400, e.getMessage());
        }
    }

    @PUT
    @Path("/{id}/protocol-participants")
    public Response setProtocolParticipants(@PathParam("id") final String id, final StringListRequest req) {
        final Channel ch = resolve(id);
        if (ch == null) {return error(404, "Channel not found: " + id);}
        final Channel updated = channelService.setProtocolParticipants(ch.id(), req.values() != null ? req.values() : List.of());
        return Response.ok(toResponse(updated)).build();
    }


    @PUT
    @Path("/{id}/delivery-tracking")
    @jakarta.transaction.Transactional
    public Response setDeliveryTracking(@PathParam("id") final String id, final DeliveryTrackingRequest req) {
        final Channel ch = resolve(id);
        if (ch == null) {return error(404, "Channel not found: " + id);}
        channelService.setTrackDelivery(ch.id(), req.enabled());
        final Channel updated = channelService.findById(ch.id()).orElseThrow();
        return Response.ok(toResponse(updated)).build();
    }


    Channel resolve(final String idOrName) {
        final UUID uuid = tryParseUuid(idOrName);
        if (uuid != null) {
            return channelService.findById(uuid).orElse(null);
        }
        return channelService.findByName(idOrName).orElse(null);
    }

    private static UUID tryParseUuid(final String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // -- Mapping ------------------------------------------------------------

    ChannelResponse toResponse(final Channel ch) {
        final long count = messageStore.countByChannel(ch.id());
        String spaceName = null;
        if (ch.spaceId() != null) {
            final var spaces = spaceStore.findByIds(List.of(ch.spaceId()));
            if (!spaces.isEmpty()) spaceName = spaces.get(0).name();
        }
        return ChannelResponse.from(ch, count, spaceName);
    }

    private List<ChannelResponse> toResponseList(final List<Channel> channels) {
        final var spaceIds = channels.stream()
                .map(Channel::spaceId).filter(Objects::nonNull)
                .distinct().collect(Collectors.toList());
        final Map<UUID, String> spaceNames = spaceIds.isEmpty()
                ? Map.of()
                : spaceStore.findByIds(spaceIds).stream()
                        .collect(Collectors.toMap(Space::id, Space::name));
        return channels.stream()
                .map(ch -> ChannelResponse.from(ch,
                        messageStore.countByChannel(ch.id()),
                        ch.spaceId() != null ? spaceNames.get(ch.spaceId()) : null))
                .collect(Collectors.toList());
    }

    static Set<MessageType> parseTypes(final Set<String> names) {
        return names.stream().map(MessageType::valueOf).collect(Collectors.toSet());
    }

    static Response error(final int status, final String message) {
        return Response.status(status)
                .entity(new ErrorResponse(message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    public record CreateChannelRequest(
            String name,
            String description,
            String semantic,
            List<String> barrierContributors,
            List<String> allowedWriters,
            List<String> adminInstances,
            List<String> reviewerInstances,
            Set<String> allowedTypes,
            Set<String> deniedTypes,
            List<String> protocols,
            List<String> protocolParticipants,
            UUID spaceId,
            Boolean trackDelivery,
            Integer rateLimitPerChannel,
            Integer rateLimitPerInstance) {}

    public record ErrorResponse(String error) {}

    public record TypeConstraintsRequest(Set<String> allowedTypes, Set<String> deniedTypes) {}

    public record RateLimitsRequest(Integer perChannel, Integer perInstance) {}

    public record StringListRequest(List<String> values) {}

    public record DeliveryTrackingRequest(Boolean enabled) {}


}
