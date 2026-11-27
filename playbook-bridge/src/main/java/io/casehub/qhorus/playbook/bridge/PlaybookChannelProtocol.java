package io.casehub.qhorus.playbook.bridge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.casehub.qhorus.api.spi.ChannelProtocol;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import io.casehub.qhorus.api.spi.SuggestedAction;
import org.jboss.logging.Logger;

public class PlaybookChannelProtocol implements ChannelProtocol {

    private static final Logger LOG = Logger.getLogger(PlaybookChannelProtocol.class);

    private final String name;
    private final String yamlContent;
    private final PlaybookExecutor executor;

    public PlaybookChannelProtocol(String name, String yamlContent, PlaybookExecutor executor) {
        this.name = name;
        this.yamlContent = yamlContent;
        this.executor = executor;
    }

    @Override
    public String protocolName() {
        return name;
    }

    @Override
    public List<DispatchAdvisory> evaluate(ProtocolContext ctx) {
        Map<String, Object> vars = buildVars(ctx);
        try {
            Map<String, Object> output = executor.execute(yamlContent, vars);
            return PlaybookProtocolOutput.parse(output, name);
        } catch (Exception e) {
            LOG.warnf(e, "Playbook protocol '%s' execution failed", name);
            return List.of(new DispatchAdvisory(
                    "playbook-bridge:execution",
                    Severity.WARNING,
                    "Protocol '" + name + "' execution failed: " + e.getMessage(),
                    Map.of(),
                    SuggestedAction.LOG));
        }
    }

    private Map<String, Object> buildVars(ProtocolContext ctx) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("channelId", ctx.channelId().toString());
        vars.put("channelName", ctx.channelName());
        vars.put("incomingType", ctx.incomingType().name());
        vars.put("sender", ctx.sender());
        if (ctx.correlationId() != null) {
            vars.put("correlationId", ctx.correlationId());
        }
        if (ctx.incomingContent() != null) {
            vars.put("incomingContent", ctx.incomingContent());
        }
        vars.put("participantCount", ctx.protocolParticipants().size());
        vars.put("recentMessageCount", ctx.recentMessages().size());
        vars.put("activeCommitmentCount", ctx.activeCommitments().size());
        if (ctx.policyOverrides() != null && !ctx.policyOverrides().isEmpty()) {
            vars.put("policyOverrides", ctx.policyOverrides());
        }
        return vars;
    }
}
