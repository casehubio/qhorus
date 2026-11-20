package io.casehub.qhorus.playbook.bridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.Severity;
import io.casehub.qhorus.api.spi.SuggestedAction;

public final class PlaybookProtocolOutput {

    private PlaybookProtocolOutput() {}

    @SuppressWarnings("unchecked")
    public static List<DispatchAdvisory> parse(Map<String, Object> output, String protocolName) {
        Object raw = output.get("advisories");
        if (!(raw instanceof List<?> entries)) {
            return List.of();
        }

        List<DispatchAdvisory> result = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> map)) {
                result.add(validationAdvisory("advisory entry is not a map"));
                continue;
            }

            String message = asString(map.get("message"));
            if (message == null) {
                result.add(validationAdvisory("advisory entry missing 'message' field"));
                continue;
            }

            Severity severity = parseSeverity(asString(map.get("severity")));
            if (severity == null) {
                result.add(validationAdvisory(
                        "invalid severity '" + map.get("severity") + "' in advisory"));
                continue;
            }

            SuggestedAction action = parseAction(asString(map.get("action")));
            Map<String, Object> evidence = parseEvidence(map.get("evidence"));

            result.add(new DispatchAdvisory(protocolName, severity, message, evidence, action));
        }
        return result;
    }

    private static DispatchAdvisory validationAdvisory(String message) {
        return new DispatchAdvisory("playbook-bridge:validation",
                Severity.WARNING, message, Map.of(), SuggestedAction.LOG);
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    private static Severity parseSeverity(String value) {
        if (value == null) return null;
        try {
            return Severity.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static SuggestedAction parseAction(String value) {
        if (value == null) return SuggestedAction.LOG;
        try {
            return SuggestedAction.valueOf(value);
        } catch (IllegalArgumentException e) {
            return SuggestedAction.LOG;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseEvidence(Object value) {
        if (value instanceof Map<?, ?> map) {
            return Collections.unmodifiableMap((Map<String, Object>) map);
        }
        return Map.of();
    }
}
