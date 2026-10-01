package io.casehub.qhorus.connector.backend.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.twilio.TwilioSmsConnector;
import io.casehub.connectors.whatsapp.WhatsAppConnector;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.ChannelSlugValidator;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfiguredAutoChannelPolicyCore implements AutoChannelPolicy {

    private static final Logger LOG = Logger.getLogger(ConfiguredAutoChannelPolicyCore.class.getName());

    private static final Map<String, String> OUTBOUND_CONVENTION = Map.of(
            InboundConnectorIds.TWILIO_SMS, TwilioSmsConnector.ID,
            InboundConnectorIds.WHATSAPP,   WhatsAppConnector.ID
    );

    private final AutoChannelEntries config;

    public ConfiguredAutoChannelPolicyCore(AutoChannelEntries config) {
        this.config = config;
        validateConfiguredPatterns();
    }

    private void validateConfiguredPatterns() {
        config.entries().forEach((connectorId, entry) ->
                entry.channelNamePattern().ifPresent(ConfiguredAutoChannelPolicyCore::validatePattern));
    }

    @Override
    public Optional<AutoChannelSpec> onFirstContact(InboundMessage msg, String lookupKey) {
        AutoChannelEntries.Entry entry = config.entries().get(msg.connectorId());
        if (entry == null || !entry.enabled()) {
            return Optional.empty();
        }

        String outboundConnectorId = entry.outboundConnectorId()
                .or(() -> java.util.Optional.ofNullable(OUTBOUND_CONVENTION.get(msg.connectorId())))
                .orElse(null);

        if (outboundConnectorId == null) {
            LOG.log(Level.SEVERE, "auto-channel enabled for connector ''{0}'' but no outbound-connector-id configured and no convention applies", msg.connectorId());
            return Optional.empty();
        }

        String outboundDestination = ConnectorKeyStrategy.deriveKey(msg);

        String channelName = entry.channelNamePattern()
                .map(p -> p.replace("{connectorId}", slugifyConnectorId(msg.connectorId()))
                            .replace("{lookupKey}", sanitiseSegment(lookupKey)))
                .orElse("connector/" + slugifyConnectorId(msg.connectorId()) + "/" + sanitiseSegment(lookupKey));

        ChannelSemantic semantic = entry.semantic()
                .map(s -> ChannelSemantic.valueOf(s.toUpperCase()))
                .orElse(ChannelSemantic.APPEND);

        String description = "Auto-created on first contact via " + msg.connectorId()
                + " from " + lookupKey;

        return Optional.of(new AutoChannelSpec(
                channelName, description, semantic, null, null,
                outboundConnectorId, outboundDestination));
    }

    public static String slugifyConnectorId(String connectorId) {
        if (connectorId == null || connectorId.isBlank()) {
            throw new IllegalArgumentException("Connector ID must not be null or blank");
        }
        String lower = connectorId.toLowerCase(Locale.ROOT);
        String slug = lower.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            throw new IllegalArgumentException(
                    "Connector ID '" + connectorId + "' reduced to empty after slugification");
        }
        if (Character.isDigit(slug.charAt(0))) {
            slug = "id-" + slug;
        }
        if (slug.length() > 80) {
            slug = slug.substring(0, 80).replaceAll("-+$", "");
        }
        if (slug.isEmpty()) {
            throw new IllegalArgumentException(
                    "Connector ID '" + connectorId + "' produced empty slug after truncation");
        }
        return slug;
    }

    public static String sanitiseSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Cannot sanitise null or blank segment");
        }
        String lowercased = raw.toLowerCase(Locale.ROOT);
        String slug = lowercased.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            throw new IllegalArgumentException(
                    "Segment '" + raw + "' reduced to empty after sanitisation");
        }
        if (Character.isDigit(slug.charAt(0))) {
            slug = "id-" + slug;
        }
        if (slug.length() > 71) {
            slug = slug.substring(0, 71).replaceAll("-+$", "");
        }
        if (slug.isEmpty()) {
            throw new IllegalArgumentException(
                    "Segment '" + raw + "' produced empty slug after truncation");
        }
        return slug + "-" + hashHex8(lowercased);
    }

    public static void validatePattern(String pattern) {
        for (String segment : pattern.split("/", -1)) {
            String testable = segment.replaceAll("\\{[^}]+}", "a");
            if (!ChannelSlugValidator.isValidSegment(testable)) {
                throw new IllegalStateException(
                        "Channel name pattern '" + pattern + "' has invalid literal segment '"
                        + segment + "' — literal parts must match [a-z][a-z0-9]*(-[a-z0-9]+)*");
            }
        }
    }

    private static String hashHex8(String lowercased) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(lowercased.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
