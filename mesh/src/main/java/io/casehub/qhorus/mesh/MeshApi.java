package io.casehub.qhorus.mesh;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.util.List;
import java.util.Map;

@McpDomain(value = "qhorus/mesh", app = "qhorus-mesh",
           summary = "Mesh relay — register, discover peers, send messages")
public interface MeshApi {

    @PlatformMutation("Register a session with the mesh relay")
    MeshRegistration meshRegister(String instanceId, String description,
                                  Map<String, String> metadata);

    @PlatformMutation("Deregister a session from the mesh relay")
    String meshDeregister(String instanceId);

    @PlatformMutation("Send a message to a channel")
    MessageResult meshSendMessage(String channel, String sender,
                                   String type, String content);

    @PlatformQuery("Check messages in a channel")
    String meshCheckMessages(String channel, Long afterId);

    @PlatformMutation("Create a channel with metadata")
    String meshCreateChannel(String name, Map<String, String> metadata);

    @PlatformQuery("List channels filtered by metadata")
    String meshListChannels(String metadataKey, String metadataValue);

    @PlatformQuery("Discover peers by metadata")
    List<PeerInfo> meshDiscoverPeers(String metadataKey, String metadataValue);
}
