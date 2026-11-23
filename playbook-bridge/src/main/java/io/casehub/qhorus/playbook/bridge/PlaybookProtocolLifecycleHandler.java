package io.casehub.qhorus.playbook.bridge;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.casehub.qhorus.api.spi.ProtocolLifecycleContext;
import io.casehub.qhorus.api.spi.ProtocolLifecycleHandler;
import io.casehub.qhorus.runtime.message.protocol.ProtocolRegistry;
import org.jboss.logging.Logger;
import org.yaml.snakeyaml.Yaml;

@ApplicationScoped
public class PlaybookProtocolLifecycleHandler implements ProtocolLifecycleHandler {

    private static final Logger LOG = Logger.getLogger(PlaybookProtocolLifecycleHandler.class);

    @Inject
    ProtocolDefinitionStore definitionStore;

    @Inject
    ProtocolDeadlineScheduler deadlineScheduler;

    @Inject
    Instance<ProtocolRegistry> protocolRegistryInstance;

    @Override
    public boolean supports(String protocolName) {
        if (protocolRegistryInstance != null && protocolRegistryInstance.isResolvable()
                && protocolRegistryInstance.get().allNames().contains(protocolName)) {
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

    @SuppressWarnings("unchecked")
    static Map<String, String> parseDeadlines(String yamlContent) {
        Map<String, Object> parsed;
        try {
            parsed = new Yaml().load(yamlContent);
        } catch (Exception e) {
            LOG.warnf("Failed to parse YAML: %s", e.getMessage());
            return Map.of();
        }
        if (parsed == null || !parsed.containsKey("deadlines")) {
            return Map.of();
        }
        Object deadlines = parsed.get("deadlines");
        if (!(deadlines instanceof Map<?, ?> deadlineMap)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : deadlineMap.entrySet()) {
            if (entry.getKey() instanceof String name && entry.getValue() != null) {
                result.put(name, entry.getValue().toString());
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
            String numPart = value.substring(0, value.length() - 1);
            if (value.endsWith("s")) return Duration.ofSeconds(Long.parseLong(numPart));
            if (value.endsWith("m")) return Duration.ofMinutes(Long.parseLong(numPart));
            if (value.endsWith("h")) return Duration.ofHours(Long.parseLong(numPart));
            return Duration.parse("PT" + value);
        } catch (Exception e) {
            LOG.warnf("Invalid deadline duration '%s'", value);
            return null;
        }
    }
}
