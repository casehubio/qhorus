package io.casehub.qhorus.runtime.api;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.runtime.ledger.CausalGraphService;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.*;
import java.util.stream.Collectors;

@Path("/api/causal-graph")
@Produces(MediaType.APPLICATION_JSON)
public class CausalGraphResource {

    @Inject
    CausalGraphService causalGraphService;

    @Inject
    MessageLedgerEntryRepository ledgerRepo;

    @Inject
    ChannelStore channelStore;

    @Inject
    CurrentPrincipal currentPrincipal;

    @GET
    @Path("/{correlationId}")
    @Transactional
    public Response getGraph(@PathParam("correlationId") String correlationId,
                             @QueryParam("limit") @DefaultValue("100") int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), 500);
        var graph = causalGraphService.buildGraph(
                correlationId, effectiveLimit, currentPrincipal.tenancyId());
        return Response.ok(graph).build();
    }

    @GET
    @Path("/attribution/{entryId}")
    @Transactional
    public Response getAttribution(@PathParam("entryId") String entryId) {
        UUID entryUuid;
        try {
            entryUuid = UUID.fromString(entryId);
        } catch (IllegalArgumentException e) {
            return Response.status(400)
                    .entity(Map.of("error", "Invalid entry ID: " + entryId))
                    .build();
        }

        List<MessageLedgerEntry> chain = ledgerRepo.findAncestorChainCrossChannel(
                entryUuid, currentPrincipal.tenancyId());

        Set<UUID> channelIds = chain.stream()
                .map(e -> e.channelId).collect(Collectors.toSet());
        Map<UUID, String> names = channelStore.findByIds(channelIds).stream()
                .collect(Collectors.toMap(Channel::id, Channel::name, (a, b) -> a));

        var result = chain.stream().map(e -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("entryId", e.id.toString());
            map.put("channelId", e.channelId.toString());
            map.put("channelName", names.getOrDefault(e.channelId, "unknown"));
            map.put("messageType", e.messageType);
            map.put("actorId", e.actorId != null ? e.actorId : "");
            map.put("correlationId", e.correlationId != null ? e.correlationId : "");
            map.put("occurredAt", e.occurredAt != null ? e.occurredAt.toString() : "");
            map.put("content", e.content != null ? e.content : "");
            map.put("causedByEntryId", e.causedByEntryId != null ? e.causedByEntryId.toString() : "");
            return map;
        }).toList();

        return Response.ok(result).build();
    }

    @Produces(MediaType.APPLICATION_JSON)
    public Response handleIllegalArgument(IllegalArgumentException e) {
        return Response.status(400)
                .entity(Map.of("error", e.getMessage()))
                .build();
    }
}
