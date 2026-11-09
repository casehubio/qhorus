package io.casehub.qhorus.mesh;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@McpDomain(value = "qhorus/mesh", app = "qhorus-mesh",
           summary = "Mesh relay — register, discover peers, send messages")
public interface MeshApi {

    @PlatformMutation("Register a session with the mesh relay")
    MeshRegistration meshRegister(String instanceId, String description,
                                  Map<String, String> metadata);

    @PlatformMutation("Deregister a session from the mesh relay")
    DeregisterResult meshDeregister(String instanceId);

    @PlatformMutation("Send a message to a channel. type: QUERY, COMMAND, RESPONSE, STATUS, DECLINE, HANDOFF, DONE, FAILURE, PROPOSE, EVENT")
    MessageResult meshSendMessage(String channel, String sender,
                                   String type, String content);

    @PlatformQuery("Check messages in a channel")
    MeshMessagePage meshCheckMessages(String channel, Optional<Long> afterId);

    @PlatformMutation("Create a channel with metadata")
    MeshChannel meshCreateChannel(String name, Map<String, String> metadata);

    @PlatformQuery("List channels, optionally filtered by metadata key/value")
    List<MeshChannel> meshListChannels(Optional<String> metadataKey, Optional<String> metadataValue);

    @PlatformQuery("Discover peers by metadata")
    List<PeerInfo> meshDiscoverPeers(String metadataKey, String metadataValue);
}
