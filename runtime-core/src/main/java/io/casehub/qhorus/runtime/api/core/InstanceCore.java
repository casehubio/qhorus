package io.casehub.qhorus.runtime.api.core;

import io.casehub.qhorus.api.instance.Instance;
import io.casehub.qhorus.api.store.InstanceStore;
import io.casehub.qhorus.api.store.query.InstanceQuery;
import io.casehub.qhorus.runtime.instance.InstanceService;

import java.util.List;
import java.util.NoSuchElementException;

public class InstanceCore {

    private final InstanceService instanceService;
    private final InstanceStore instanceStore;

    public InstanceCore(InstanceService instanceService, InstanceStore instanceStore) {
        this.instanceService = instanceService;
        this.instanceStore = instanceStore;
    }

    public InstanceResponse register(RegisterInstanceRequest req) {
        Instance inst = instanceService.register(
                req.instanceId(), req.description(),
                req.capabilities() != null ? req.capabilities() : List.of(),
                null, false, req.metadata());
        List<String> caps = instanceStore.findCapabilities(inst.id());
        return InstanceResponse.from(inst, caps);
    }

    public void deregister(String instanceId) {
        instanceStore.findByInstanceId(instanceId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Instance not found: " + instanceId));
        instanceService.deregister(instanceId);
    }

    public List<InstanceResponse> list(String capability) {
        List<Instance> instances;
        if (capability != null && !capability.isBlank()) {
            instances = instanceStore.scan(InstanceQuery.byCapability(capability));
        } else {
            instances = instanceStore.scan(InstanceQuery.all());
        }
        return instances.stream()
                .map(inst -> InstanceResponse.from(inst,
                        instanceStore.findCapabilities(inst.id())))
                .toList();
    }

    public InstanceResponse get(String instanceId) {
        Instance inst = instanceStore.findByInstanceId(instanceId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Instance not found: " + instanceId));
        List<String> caps = instanceStore.findCapabilities(inst.id());
        return InstanceResponse.from(inst, caps);
    }
}
