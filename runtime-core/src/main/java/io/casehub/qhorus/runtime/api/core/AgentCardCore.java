package io.casehub.qhorus.runtime.api.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.spi.AgentCardSigner;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.runtime.instance.InstanceService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AgentCardCore {

    private final QhorusConfig config;
    private final CurrentPrincipal currentPrincipal;
    private final InstanceService instanceService;
    private final boolean pushAvailable;
    private final AgentCardSigner agentCardSigner;
    private final ObjectMapper objectMapper;

    public AgentCardCore(QhorusConfig config,
                         CurrentPrincipal currentPrincipal,
                         InstanceService instanceService,
                         boolean pushAvailable,
                         AgentCardSigner agentCardSigner,
                         ObjectMapper objectMapper) {
        this.config = config;
        this.currentPrincipal = currentPrincipal;
        this.instanceService = instanceService;
        this.pushAvailable = pushAvailable;
        this.agentCardSigner = agentCardSigner;
        this.objectMapper = objectMapper;
    }

    public Object getAgentCard() {
        QhorusConfig.AgentCard cfg = config.agentCard();

        List<io.casehub.a2a.model.AgentCard.AgentRef> agents = instanceService.listAll().stream()
                .map(inst -> new io.casehub.a2a.model.AgentCard.AgentRef(
                        inst.instanceId(),
                        "/.well-known/agents/" + inst.instanceId() + ".json"))
                .toList();

        var card = new io.casehub.a2a.model.AgentCard(
                cfg.name(),
                cfg.description(),
                cfg.url().orElse(""),
                cfg.version(),
                buildSkills(),
                new io.casehub.a2a.model.AgentCapabilities(true, pushAvailable),
                Map.of("schemes", List.of("bearer")),
                currentPrincipal.tenancyId(),
                agents);

        return trySign(card);
    }

    public Optional<Object> getPerAgentCard(String instanceId) {
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
                            new io.casehub.a2a.model.AgentCapabilities(true, pushAvailable),
                            null,
                            currentPrincipal.tenancyId(),
                            null);
                    return trySign(card);
                });
    }

    public Optional<Object> getJwks() {
        if (agentCardSigner == null) {
            return Optional.empty();
        }
        return Optional.of(agentCardSigner.jwks());
    }

    private Object trySign(Object card) {
        if (agentCardSigner != null) {
            try {
                String cardJson = objectMapper.valueToTree(card).toString();
                return agentCardSigner.sign(cardJson);
            } catch (Exception e) {
                // Fall through to unsigned
            }
        }
        return card;
    }

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
