package io.casehub.qhorus.api.store;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.casehub.qhorus.api.channel.ChannelConnectorBinding;

public interface ChannelBindingStore {

    Optional<ChannelConnectorBinding> findByChannelId(UUID channelId);

    Optional<ChannelConnectorBinding> findByKey(String inboundConnectorId, String externalKey);

    void put(ChannelConnectorBinding binding);

    /**
     * Atomically creates the binding if no binding exists for the same (inboundConnectorId, externalKey).
     *
     * @return the existing binding if one was already present; empty if the new binding was created
     */
    default Optional<ChannelConnectorBinding> putIfAbsent(ChannelConnectorBinding binding) {
        Optional<ChannelConnectorBinding> existing = findByKey(binding.inboundConnectorId(), binding.externalKey());
        if (existing.isPresent()) {
            return existing;
        }
        put(binding);
        return Optional.empty();
    }

    void delete(UUID channelId);

    /** Returns a snapshot of all bindings keyed by channelId. Callers must not mutate the map. */
    Map<UUID, ChannelConnectorBinding> findAll();
}
