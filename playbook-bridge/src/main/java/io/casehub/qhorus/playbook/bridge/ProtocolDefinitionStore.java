package io.casehub.qhorus.playbook.bridge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProtocolDefinitionStore {

    Optional<ProtocolDefinition> findByChannelAndProtocol(UUID channelId, String protocolName);

    List<ProtocolDefinition> findByChannel(UUID channelId);

    boolean existsByProtocolName(String protocolName);

    void put(ProtocolDefinition definition);

    void delete(UUID channelId, String protocolName);

    void deleteByChannel(UUID channelId);
}
