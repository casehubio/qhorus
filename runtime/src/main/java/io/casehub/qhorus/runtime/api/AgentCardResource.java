package io.casehub.qhorus.runtime.api;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/.well-known")
@ApplicationScoped
public class AgentCardResource {

    @Inject
    QhorusConfig config;

    @Inject
    CurrentPrincipal currentPrincipal;

    @Inject
    io.casehub.qhorus.runtime.instance.InstanceService instanceService;
    @Inject
    jakarta.enterprise.inject.Instance<io.casehub.qhorus.api.store.PushNotificationConfigStore> pushStore;


    @GET
    @Path("/agent.json")
    @Produces(MediaType.APPLICATION_JSON)
    public io.casehub.a2a.model.AgentCard getAgentCard() {
        QhorusConfig.AgentCard cfg = config.agentCard();

        List<io.casehub.a2a.model.AgentCard.AgentRef> agents = instanceService.listAll().stream()
                                                                              .map(inst -> new io.casehub.a2a.model.AgentCard.AgentRef(
                                                                                      inst.instanceId(),
                                                                                      "/.well-known/agents/" + inst.instanceId() + ".json"))
                                                                              .toList();

        return new io.casehub.a2a.model.AgentCard(
                cfg.name(),
                cfg.description(),
                cfg.url().orElse(""),
                cfg.version(),
                buildSkills(),
                new io.casehub.a2a.model.AgentCapabilities(true, pushStore.isResolvable()),
                java.util.Map.of("schemes", java.util.List.of("bearer")),
                currentPrincipal.tenancyId(),
                agents);
    }

    @GET
    @Path("/agents/{instanceId}.json")
    @Produces(MediaType.APPLICATION_JSON)
    public jakarta.ws.rs.core.Response getPerAgentCard(
            @jakarta.ws.rs.PathParam("instanceId") String instanceId) {
        return instanceService.findByInstanceId(instanceId)
                              .map(inst -> {
                                  List<String> capTags = instanceService.findCapabilityTagsForInstance(instanceId);
                                  List<io.casehub.a2a.model.AgentSkill> skills = capTags.stream()
                                                                                        .map(cap -> new io.casehub.a2a.model.AgentSkill(cap, cap, null))
                                                                                        .toList();
                                  var card = new io.casehub.a2a.model.AgentCard(
                                          inst.instanceId(),
                                          inst.description(),
                                          "/.well-known/agents/" + inst.instanceId() + ".json",
                                          config.agentCard().version(),
                                          skills,
                                          new io.casehub.a2a.model.AgentCapabilities(true, pushStore.isResolvable()),
                                          null,
                                          currentPrincipal.tenancyId(),
                                          null);
                                  return jakarta.ws.rs.core.Response.ok(card).build();
                              })
                              .orElse(jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.NOT_FOUND)
                                                                 .entity(new ErrorResponse("Instance not found: " + instanceId))
                                                                 .type(MediaType.APPLICATION_JSON).build());}

    private List<io.casehub.a2a.model.AgentSkill> buildSkills() {
        return List.of(
                new io.casehub.a2a.model.AgentSkill(
                        "channel-messaging",
                        "Channel Messaging",
                        "Send and receive typed messages on named channels with declared semantics"
                        + " (APPEND, COLLECT, BARRIER, EPHEMERAL, LAST_WRITE)"),
                new io.casehub.a2a.model.AgentSkill(
                        "shared-data",
                        "Shared Data Store",
                        "Store and retrieve large artefacts by key with UUID references,"
                        + " claim/release lifecycle, and chunked streaming"),
                new io.casehub.a2a.model.AgentSkill(
                        "presence",
                        "Agent Presence",
                        "Register agents with capability tags and discover online peers"
                        + " by capability tag or role broadcast"),
                new io.casehub.a2a.model.AgentSkill(
                        "wait-for-reply",
                        "Correlation-based Wait",
                        "Wait for a response with a specific correlation ID —"
                        + " safe under concurrent requests via UUID-keyed CommitmentStore"));
    }
}
