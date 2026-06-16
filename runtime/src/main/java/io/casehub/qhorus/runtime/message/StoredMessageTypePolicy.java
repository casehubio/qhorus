package io.casehub.qhorus.runtime.message;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageTypeViolationException;
import io.casehub.qhorus.runtime.channel.Channel;

@ApplicationScoped
public class StoredMessageTypePolicy implements MessageTypePolicy {

    /**
     * Hard-enforces COMMAND and QUERY only — both call commitmentService.open(); advisory
     * dispatch on the wrong channel creates orphan Commitments. No-op for all other types.
     */
    @Override
    public void validate(Channel channel, MessageType type) {
        if (type != MessageType.COMMAND && type != MessageType.QUERY) return;
        // Denial-first: explicit denial wins over allowedTypes
        if (channel.deniedTypes != null && !channel.deniedTypes.isBlank()) {
            if (MessageType.parseTypes(channel.deniedTypes).contains(type)) {
                throw MessageTypeViolationException.denied(channel.name, type, channel.deniedTypes);
            }
        }
        // Open channel (no allowedTypes restriction) passes after denial check
        if (channel.allowedTypes == null || channel.allowedTypes.isBlank()) return;
        if (!MessageType.parseTypes(channel.allowedTypes).contains(type)) {
            throw new MessageTypeViolationException(channel.name, type, channel.allowedTypes);
        }
    }

    /**
     * Advisory for non-obligation-creating types only. Returns null for COMMAND and QUERY
     * (those are hard-enforced by validate()). Denial-first: denial wins over allowedTypes.
     */
    @Override
    public String advisory(Channel channel, MessageType type) {
        if (type == MessageType.COMMAND || type == MessageType.QUERY) return null;
        if (channel.deniedTypes != null && !channel.deniedTypes.isBlank()) {
            if (MessageType.parseTypes(channel.deniedTypes).contains(type)) {
                return "Type advisory: channel '" + channel.name
                        + "' explicitly denies " + type
                        + " — denied: [" + channel.deniedTypes + "]. Message dispatched.";
            }
        }
        if (channel.allowedTypes == null || channel.allowedTypes.isBlank()) return null;
        if (!MessageType.parseTypes(channel.allowedTypes).contains(type)) {
            return "Type advisory: channel '" + channel.name
                    + "' allows [" + channel.allowedTypes + "] only, received " + type
                    + ". Message dispatched.";
        }
        return null;
    }
}
