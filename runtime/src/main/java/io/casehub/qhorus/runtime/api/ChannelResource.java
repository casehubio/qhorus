package io.casehub.qhorus.runtime.api;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.MembershipManager;
import io.casehub.qhorus.api.channel.PresenceTracker;
import io.casehub.qhorus.api.channel.ReactionManager;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.channel.TopicManager;
import io.casehub.qhorus.api.event.ChannelMutationEvent;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.Reaction;
import io.casehub.qhorus.api.store.CommitmentReader;
import io.casehub.qhorus.api.store.MembershipReader;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.ReactionReader;
import io.casehub.qhorus.api.store.SpaceStore;
import io.casehub.qhorus.api.store.TopicReader;
import io.casehub.qhorus.api.store.query.ChannelQuery;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.dashboard.QhorusDashboardService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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

    @Inject ChannelService channelService;
    @Inject MessageStore messageStore;
    @Inject SpaceStore spaceStore;
    @Inject QhorusDashboardService dashboard;
    @Inject ReactionManager reactionManager;
    @Inject ReactionReader reactionReader;
    @Inject TopicManager topicManager;
    @Inject TopicReader topicReader;
    @Inject MembershipManager membershipManager;
    @Inject MembershipReader membershipReader;
    @Inject PresenceTracker presenceTracker;
    @Inject CommitmentReader commitmentReader;
    @Inject ConsumerMessaging messaging;
    @Inject Event<ChannelMutationEvent> mutationEvent;

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

    // -- Aggregation --------------------------------------------------------

    @GET
    @Path("/feed")
    public List<Map<String, Object>> feed(@QueryParam("limit") @DefaultValue("50") int limit) {
        return dashboard.getFeed(limit);
    }

    @GET
    @Path("/{id}/timeline")
    public Response timeline(@PathParam("id") final String id,
                             @QueryParam("after") Long after,
                             @QueryParam("limit") @DefaultValue("100") int limit) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        return Response.ok(dashboard.getTimeline(ch.name(), after != null ? after : 0, limit)).build();
    }

    // -- Reactions ----------------------------------------------------------

    @POST
    @Path("/{id}/messages/{messageId}/reactions")
    @Transactional
    public Response addReaction(@PathParam("id") final String id,
                                @PathParam("messageId") String messageId,
                                ReactionRequest request) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        long msgId = parseLongParam(messageId, "messageId");
        reactionManager.react(msgId, request.emoji());
        mutationEvent.fire(new ChannelMutationEvent.ReactionAdded(msgId, request.emoji()));
        return Response.ok().build();
    }

    @DELETE
    @Path("/{id}/messages/{messageId}/reactions/{emoji}")
    @Transactional
    public Response removeReaction(@PathParam("id") final String id,
                                   @PathParam("messageId") String messageId,
                                   @PathParam("emoji") String emoji) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        long msgId = parseLongParam(messageId, "messageId");
        reactionManager.unreact(msgId, emoji);
        mutationEvent.fire(new ChannelMutationEvent.ReactionRemoved(msgId, emoji));
        return Response.ok().build();
    }

    @GET
    @Path("/{id}/messages/{messageId}/reactions")
    public Response listReactions(@PathParam("id") final String id,
                                  @PathParam("messageId") String messageId) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        var reactions = reactionReader.findByMessage(parseLongParam(messageId, "messageId")).stream()
            .map(Reaction::emoji).toList();
        return Response.ok(reactions).build();
    }

    // -- Topics -------------------------------------------------------------

    @POST
    @Path("/{id}/topics")
    @Transactional
    public Response createTopic(@PathParam("id") final String id,
                                CreateTopicRequest request) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        String name = request.name() != null ? request.name().trim() : "";
        if (name.isEmpty()) return error(400, "Topic name must not be empty");
        if (name.length() > 100) return error(400, "Topic name must be 100 characters or less");
        if ("General".equals(name) || "general".equals(name)) return error(409, "\"General\" is reserved");
        var existing = topicReader.find(ch.id(), name);
        if (existing.isPresent()) return error(409, "Topic already exists");
        var topic = topicManager.create(ch.id(), name);
        mutationEvent.fire(new ChannelMutationEvent.TopicCreated(ch.id(), topic));
        return Response.ok(Map.of("id", String.valueOf(topic.id()), "name", topic.name())).build();
    }

    @GET
    @Path("/{id}/topics")
    public Response listTopics(@PathParam("id") final String id) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        return Response.ok(topicManager.listTopics(ch.id())).build();
    }

    @PUT
    @Path("/{id}/topics/{topicId}")
    @Transactional
    public Response updateTopic(@PathParam("id") final String id,
                                @PathParam("topicId") String topicId,
                                UpdateTopicRequest request) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        long topicLongId = parseLongParam(topicId, "topicId");
        var existing = topicReader.findById(topicLongId);
        if (existing.isEmpty()) return error(404, "Topic not found");
        if (!ch.id().equals(existing.get().channelId())) return error(400, "Topic does not belong to this channel");
        if (request.name() != null) {
            var trimmed = request.name().trim();
            if (trimmed.isEmpty() || trimmed.length() > 100) return error(400, "Invalid topic name");
            topicManager.rename(ch.id(), existing.get().name(), trimmed);
        }
        if (request.state() != null) {
            if ("RESOLVED".equals(request.state())) topicManager.resolve(ch.id(), existing.get().name());
            else if ("ACTIVE".equals(request.state()) && existing.get().resolved()) topicManager.unresolve(ch.id(), existing.get().name());
        }
        var updated = topicReader.findById(topicLongId).orElse(existing.get());
        mutationEvent.fire(new ChannelMutationEvent.TopicUpdated(ch.id(), updated));
        return Response.ok(Map.of("ok", true)).build();
    }

    @POST
    @Path("/{id}/topics/{topicId}/merge")
    @Transactional
    public Response mergeTopic(@PathParam("id") final String id,
                               @PathParam("topicId") String topicId,
                               MergeTopicRequest request) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        long sourceTopicId = parseLongParam(topicId, "topicId");
        var source = topicReader.findById(sourceTopicId);
        if (source.isEmpty()) return error(404, "Source topic not found");
        if (!ch.id().equals(source.get().channelId())) return error(400, "Source topic does not belong to this channel");
        if ("general".equalsIgnoreCase(source.get().name())) return error(400, "Cannot merge the default topic");
        long targetTopicId = parseLongParam(request.targetTopicId(), "targetTopicId");
        var target = topicReader.findById(targetTopicId);
        if (target.isEmpty()) return error(404, "Target topic not found");
        if (!ch.id().equals(target.get().channelId())) return error(400, "Target topic does not belong to this channel");
        topicManager.merge(ch.id(), source.get().name(), target.get().name());
        mutationEvent.fire(new ChannelMutationEvent.TopicRemoved(ch.id(), sourceTopicId));
        var updatedTarget = topicReader.findById(targetTopicId).orElse(target.get());
        mutationEvent.fire(new ChannelMutationEvent.TopicUpdated(ch.id(), updatedTarget));
        return Response.ok(Map.of("ok", true)).build();
    }

    // -- Members ------------------------------------------------------------

    @GET
    @Path("/{id}/members")
    public Response listMembers(@PathParam("id") final String id) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        return Response.ok(membershipReader.findByChannel(ch.id())).build();
    }

    @POST
    @Path("/{id}/members")
    @Transactional
    public Response addMember(@PathParam("id") final String id,
                              AddMemberRequest request) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        var membership = membershipManager.join(ch.id(), request.memberId());
        mutationEvent.fire(new ChannelMutationEvent.MemberJoined(ch.id(), membership));
        return Response.ok().build();
    }

    @DELETE
    @Path("/{id}/members/{memberId}")
    @Transactional
    public Response removeMember(@PathParam("id") final String id,
                                 @PathParam("memberId") String memberId) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        membershipManager.leave(ch.id(), memberId);
        mutationEvent.fire(new ChannelMutationEvent.MemberLeft(ch.id(), memberId));
        return Response.ok().build();
    }

    // -- Presence -----------------------------------------------------------

    @GET
    @Path("/{id}/presence")
    public Response listPresence(@PathParam("id") final String id) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        return Response.ok(presenceTracker.getChannelPresence(ch.id())).build();
    }

    // -- Commitments --------------------------------------------------------

    @GET
    @Path("/{id}/commitments")
    public Response listCommitments(@PathParam("id") final String id) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        return Response.ok(commitmentReader.findByChannel(ch.id())).build();
    }

    // -- Correlation --------------------------------------------------------

    @GET
    @Path("/{id}/correlation/{correlationId}")
    public Response correlationChain(@PathParam("id") final String id,
                                     @PathParam("correlationId") String correlationId) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        return Response.ok(messaging.findAllByCorrelationId(correlationId)).build();
    }

    // -- Messages (read-only, via qhorus) -----------------------------------

    @POST
    @Path("/{id}/messages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response postMessage(@PathParam("id") final String id,
                                MessagePostRequest request) {
        final Channel ch = resolve(id);
        if (ch == null) return error(404, "Channel not found: " + id);
        var dispatch = io.casehub.qhorus.api.message.MessageDispatch.builder()
            .channelId(ch.id())
            .sender(request.sender())
            .type(MessageType.valueOf(request.type()))
            .actorType(io.casehub.platform.api.identity.ActorType.valueOf(request.actorType()))
            .content(request.content())
            .build();
        var result = messaging.dispatch(dispatch);
        return Response.ok(Map.of("messageId", result.messageId())).build();
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

    @PUT
    @Path("/{id}/enforcement-mode")
    @jakarta.transaction.Transactional
    public Response setEnforcementMode(@PathParam("id") final String id, final EnforcementModeRequest req) {
        Channel ch = resolve(id);
        if (ch == null) {return error(404, "Channel not found: " + id);}
        if (req.mode() != null) {
            io.casehub.qhorus.api.channel.EnforcementMode mode;
            try {
                mode = io.casehub.qhorus.api.channel.EnforcementMode.valueOf(req.mode().toUpperCase());
            } catch (IllegalArgumentException e) {
                return error(400, "Invalid enforcement mode: " + req.mode());
            }
            ch = channelService.setEnforcementMode(ch.id(), mode);
        }
        if (req.exclusions() != null) {
            ch = channelService.setEnforcementExclusions(ch.id(), req.exclusions());
        }
        return Response.ok(toResponse(ch)).build();
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

    static long parseLongParam(final String value, final String name) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value);
        }
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

    public record ReactionRequest(String emoji) {}
    public record AddMemberRequest(String memberId) {}
    public record CreateTopicRequest(String name) {}
    public record UpdateTopicRequest(String name, String state) {}
    public record MergeTopicRequest(String targetTopicId) {}
    public record MessagePostRequest(String sender, String type, String actorType, String content) {}

    public record EnforcementModeRequest(String mode, java.util.List<String> exclusions) {}


    @org.jboss.resteasy.reactive.server.ServerExceptionMapper
    Response handleIllegalArgument(IllegalArgumentException e) {
        return error(400, e.getMessage());
    }
}
