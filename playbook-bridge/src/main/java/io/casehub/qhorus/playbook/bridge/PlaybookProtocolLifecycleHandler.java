package io.casehub.qhorus.playbook.bridge;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.qhorus.api.spi.ProtocolLifecycleContext;
import io.casehub.qhorus.api.spi.ProtocolLifecycleHandler;
import io.casehub.qhorus.runtime.message.protocol.ProtocolRegistry;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PlaybookProtocolLifecycleHandler implements ProtocolLifecycleHandler {

    private static final Logger LOG = Logger.getLogger(PlaybookProtocolLifecycleHandler.class);

    @Inject
    ProtocolDefinitionStore definitionStore;

    @Inject
    ProtocolDeadlineScheduler deadlineScheduler;

    ProtocolRegistry protocolRegistry;

    @Override
    public boolean supports(String protocolName) {
        if (protocolRegistry != null && protocolRegistry.allNames().contains(protocolName)) {
            return false;
        }
        return definitionStore.existsByProtocolName(protocolName);
    }

    @Override
    public void onInit(ProtocolLifecycleContext context) {
        Optional<ProtocolDefinition> def = definitionStore.findByChannelAndProtocol(
                context.channelId(), context.protocolName());
        if (def.isEmpty()) {
            LOG.warnf("No playbook definition for protocol '%s' on channel %s during init",
                    context.protocolName(), context.channelId());
            return;
        }

        Map<String, String> deadlines = parseDeadlines(def.get().yamlContent());
        if (deadlines.isEmpty()) {
            return;
        }

        Instant baseTime = context.recovered() ? recoverBaseTime(context) : Instant.now();

        for (Map.Entry<String, String> entry : deadlines.entrySet()) {
            Duration duration = parseDuration(entry.getValue());
            if (duration != null) {
                DeadlineKey key = new DeadlineKey(
                        context.channelId(), context.protocolName(), entry.getKey());
                deadlineScheduler.register(key, baseTime.plus(duration));
            }
        }
    }

    @Override
    public void onDestroy(UUID channelId, String protocolName) {
        deadlineScheduler.clear(channelId, protocolName);
    }

    Map<String, String> parseDeadlines(String yamlContent) {
        int idx = yamlContent.indexOf("deadlines:");
        if (idx < 0) return Map.of();

        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        String[] lines = yamlContent.substring(idx).split("\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (!line.startsWith("  ") && !line.startsWith("\t")) break;
            String trimmed = line.trim();
            int colon = trimmed.indexOf(':');
            if (colon > 0 && colon < trimmed.length() - 1) {
                String name = trimmed.substring(0, colon).trim();
                String value = trimmed.substring(colon + 1).trim();
                if (!name.isEmpty() && !value.isEmpty()) {
                    result.put(name, value);
                }
            }
        }
        return result;
    }

    private Instant recoverBaseTime(ProtocolLifecycleContext context) {
        if (!context.messageHistory().isEmpty()) {
            var lastMessage = context.messageHistory().get(context.messageHistory().size() - 1);
            return lastMessage.createdAt();
        }
        return Instant.now();
    }

    static Duration parseDuration(String value) {
        try {
            if (value.startsWith("PT") || value.startsWith("P")) {
                return Duration.parse(value);
            }
            if (value.endsWith("s")) return Duration.ofSeconds(Long.parseLong(value.replace("s", "")));
            if (value.endsWith("m")) return Duration.ofMinutes(Long.parseLong(value.replace("m", "")));
            if (value.endsWith("h")) return Duration.ofHours(Long.parseLong(value.replace("h", "")));
            return Duration.parse("PT" + value);
        } catch (Exception e) {
            LOG.warnf("Invalid deadline duration '%s'", value);
            return null;
        }
    }
}
