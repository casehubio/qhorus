package io.casehub.qhorus.playbook.bridge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryProtocolDefinitionStore implements ProtocolDefinitionStore {

    private final ConcurrentHashMap<String, ProtocolDefinition> store = new ConcurrentHashMap<>();

    private static String key(UUID channelId, String protocolName) {
        return channelId + ":" + protocolName;
    }

    @Override
    public Optional<ProtocolDefinition> findByChannelAndProtocol(UUID channelId, String protocolName) {
        return Optional.ofNullable(store.get(key(channelId, protocolName)));
    }

    @Override
    public List<ProtocolDefinition> findByChannel(UUID channelId) {
        return store.values().stream()
                .filter(d -> d.channelId().equals(channelId))
                .toList();
    }

    @Override
    public boolean existsByProtocolName(String protocolName) {
        return store.values().stream()
                .anyMatch(d -> d.protocolName().equals(protocolName));
    }

    @Override
    public void put(ProtocolDefinition definition) {
        store.put(key(definition.channelId(), definition.protocolName()), definition);
    }

    @Override
    public void delete(UUID channelId, String protocolName) {
        store.remove(key(channelId, protocolName));
    }

    @Override
    public void deleteByChannel(UUID channelId) {
        store.entrySet().removeIf(e -> e.getValue().channelId().equals(channelId));
    }
}
