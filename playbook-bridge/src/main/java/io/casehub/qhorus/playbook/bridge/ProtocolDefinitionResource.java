package io.casehub.qhorus.playbook.bridge;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.store.ChannelStore;
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
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Path("/api/channels/{channelId}/protocol-definitions")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class ProtocolDefinitionResource {

    private static final Logger LOG = Logger.getLogger(ProtocolDefinitionResource.class);

    @Inject
    ProtocolDefinitionStore definitionStore;

    @Inject
    CompilationCache compilationCache;
    @Inject
    ChannelStore     channelStore;
    @Inject
    ChannelManager   channelManager;
    @Inject
    CurrentPrincipal currentPrincipal;


    @GET
    public List<ProtocolDefinition> list(@PathParam("channelId") UUID channelId) {
        return definitionStore.findByChannel(channelId);
    }

    @GET
    @Path("/{protocolName}")
    public Response get(@PathParam("channelId") UUID channelId,
                        @PathParam("protocolName") String protocolName) {
        return definitionStore.findByChannelAndProtocol(channelId, protocolName)
                .map(def -> Response.ok(def).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @PUT
    @Path("/{protocolName}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response put(@PathParam("channelId") UUID channelId,
                        @PathParam("protocolName") String protocolName,
                        ProtocolDefinitionRequest request) {
        if (request.yamlContent() == null || request.yamlContent().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\":\"yamlContent is required\"}")
                           .build();
        }

        String validationError = validateYaml(request.yamlContent());
        if (validationError != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\":\"" + validationError + "\"}")
                           .build();
        }

        String tenancyId = currentPrincipal.tenancyId();
        ProtocolDefinition def = new ProtocolDefinition(
                UUID.randomUUID(), channelId, protocolName,
                request.yamlContent(), tenancyId, Instant.now());
        definitionStore.put(def);
        compilationCache.evict(channelId, protocolName);

        channelStore.find(channelId).ifPresent(channel -> {
            if (!channel.protocols().contains(protocolName)) {
                var updated = new java.util.ArrayList<>(channel.protocols());
                updated.add(protocolName);
                channelManager.setProtocols(channelId, updated);
            }
        });

        LOG.infof("Stored protocol definition '%s' for channel %s", protocolName, channelId);
        return Response.ok(def).build();
    }

    @DELETE
    @Path("/{protocolName}")
    public Response delete(@PathParam("channelId") UUID channelId,
                           @PathParam("protocolName") String protocolName) {
        definitionStore.delete(channelId, protocolName);
        compilationCache.evict(channelId, protocolName);

        channelStore.find(channelId).ifPresent(channel -> {
            if (channel.protocols().contains(protocolName)) {
                var updated = new java.util.ArrayList<>(channel.protocols());
                updated.remove(protocolName);
                channelManager.setProtocols(channelId, updated);
            }
        });

        LOG.infof("Deleted protocol definition '%s' for channel %s", protocolName, channelId);
        return Response.noContent().build();
    }

    private String validateYaml(String yamlContent) {
        if (yamlContent.length() > 65536) {
            return "YAML content exceeds maximum size (64KB)";
        }

        int idx = yamlContent.indexOf("deadlines:");
        if (idx >= 0) {
            String[] lines = yamlContent.substring(idx).split("\n");
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (!line.startsWith("  ") && !line.startsWith("\t")) break;
                String trimmed = line.trim();
                int colon = trimmed.indexOf(':');
                if (colon > 0 && colon < trimmed.length() - 1) {
                    String value = trimmed.substring(colon + 1).trim();
                    if (!value.isEmpty()
                            && PlaybookProtocolLifecycleHandler.parseDuration(value) == null) {
                        return "Invalid deadline duration: " + value;
                    }
                }
            }
        }
        return null;
    }

    public record ProtocolDefinitionRequest(String yamlContent, String tenancyId) {}
}
