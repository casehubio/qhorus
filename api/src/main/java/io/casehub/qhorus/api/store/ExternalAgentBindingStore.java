package io.casehub.qhorus.api.store;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.qhorus.api.instance.ExternalAgentBinding;

public interface ExternalAgentBindingStore {

    void put(ExternalAgentBinding binding);

    Optional<ExternalAgentBinding> findByInstanceId(String instanceId);

    List<ExternalAgentBinding> findAll();

    void delete(UUID id);

    void deleteByInstanceId(String instanceId);
}
