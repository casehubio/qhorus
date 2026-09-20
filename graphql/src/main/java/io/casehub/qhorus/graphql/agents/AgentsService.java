package io.casehub.qhorus.graphql.agents;

import io.casehub.qhorus.api.channel.Presence;
import io.casehub.qhorus.api.channel.PresenceStatus;
import io.casehub.qhorus.api.channel.PresenceTracker;
import io.casehub.qhorus.api.instance.Instance;
import io.casehub.qhorus.api.instance.InstanceInfo;
import io.casehub.qhorus.api.instance.InstanceManager;
import io.casehub.qhorus.api.instance.RegisterResponse;
import io.casehub.qhorus.api.spi.agents.AgentsApi;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AgentsService implements AgentsApi {

    private final InstanceManager instanceManager;
    private final PresenceTracker presenceTracker;

    public AgentsService(InstanceManager instanceManager,
                         PresenceTracker presenceTracker) {
        this.instanceManager = instanceManager;
        this.presenceTracker = presenceTracker;
    }

    @Override
    public List<InstanceInfo> instances(String capability) {
        if (capability != null && !capability.isBlank()) {
            return instanceManager.findInfoByCapability(capability);
        }
        return instanceManager.listInfo();
    }

    @Override
    public InstanceInfo instance(String instanceId) {
        return instanceManager.findInfo(instanceId);
    }

    @Override
    public Presence presence(String memberId) {
        return presenceTracker.getPresence(memberId);
    }

    @Override
    public List<Presence> channelPresence(UUID channelId) {
        return presenceTracker.getChannelPresence(channelId);
    }

    @Override
    public RegisterResponse register(String instanceId, String description,
                                     List<String> capabilities, Boolean readOnly) {
        List<String> caps = capabilities != null ? capabilities : List.of();
        boolean ro = readOnly != null && readOnly;
        Instance instance = instanceManager.register(instanceId, description, caps, ro);
        List<InstanceInfo> online = instanceManager.listInfo();
        return new RegisterResponse(instance.instanceId(), List.of(), online);
    }

    @Override
    public boolean deregister(String instanceId) {
        instanceManager.deregister(instanceId);
        return true;
    }

    @Override
    public Presence setPresence(String status, String statusMessage, String memberId) {
        presenceTracker.heartbeat(PresenceStatus.valueOf(status), statusMessage);
        return presenceTracker.getPresence(memberId);
    }
}
