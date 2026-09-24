package io.casehub.qhorus.api.spi.agents;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.qhorus.api.channel.Presence;
import io.casehub.qhorus.api.instance.InstanceInfo;
import io.casehub.qhorus.api.instance.RegisterResponse;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "qhorus/agents", app = "qhorus", summary = "Agent registration, capabilities, and lifecycle in channels")
public interface AgentsApi {

    @PlatformQuery("List registered agent instances, optionally filtered by capability tag")
    List<InstanceInfo> instances(String capability);

    @PlatformQuery("Look up a registered instance by its ID")
    InstanceInfo instance(String instanceId);

    @PlatformQuery("Get presence status for a member")
    Presence presence(String memberId);

    @PlatformQuery("Get presence status for all members of a channel")
    List<Presence> channelPresence(UUID channelId);

    @PlatformMutation("Register an agent instance with capability tags")
    RegisterResponse register(String instanceId, String description,
                              List<String> capabilities, Boolean readOnly);

    @PlatformMutation("Force-remove an agent instance from the registry")
    boolean deregister(String instanceId);

    @PlatformMutation("Report presence status heartbeat (ONLINE, AVAILABLE, BUSY)")
    Presence setPresence(String status, String statusMessage, String memberId);
}
