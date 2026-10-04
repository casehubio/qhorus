package io.casehub.qhorus.agent.bridge;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AgentChannelBinding(
        UUID id,
        UUID channelId,
        String agentInstanceId,
        String backendKey,
        String agentBriefing,
        List<String> mcpServers,
        boolean persistent,
        int maxConcurrency,
        int contextWindowSize,
        Map<String, String> metadata,
        String tenancyId) {

    public AgentChannelBinding {
        mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : null;
        if (persistent && maxConcurrency != 1) {
            throw new IllegalArgumentException(
                    "Persistent sessions require maxConcurrency=1, got " + maxConcurrency);
        }
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException(
                    "maxConcurrency must be >= 1, got " + maxConcurrency);
        }
        if (contextWindowSize < 0) {
            throw new IllegalArgumentException(
                    "contextWindowSize must be >= 0, got " + contextWindowSize);
        }
    }

    public static Builder builder(UUID channelId, String agentInstanceId, String backendKey) {
        return new Builder(channelId, agentInstanceId, backendKey);
    }

    public static final class Builder {
        private final UUID channelId;
        private final String agentInstanceId;
        private final String backendKey;
        private UUID id = UUID.randomUUID();
        private String agentBriefing = "";
        private List<String> mcpServers = List.of();
        private boolean persistent = true;
        private int maxConcurrency = 1;
        private int contextWindowSize = 20;
        private Map<String, String> metadata;
        private String tenancyId;

        Builder(UUID channelId, String agentInstanceId, String backendKey) {
            this.channelId = channelId;
            this.agentInstanceId = agentInstanceId;
            this.backendKey = backendKey;
        }

        public Builder id(UUID v) { this.id = v; return this; }
        public Builder agentBriefing(String v) { this.agentBriefing = v; return this; }
        public Builder mcpServers(List<String> v) { this.mcpServers = v; return this; }
        public Builder persistent(boolean v) { this.persistent = v; return this; }
        public Builder maxConcurrency(int v) { this.maxConcurrency = v; return this; }
        public Builder contextWindowSize(int v) { this.contextWindowSize = v; return this; }
        public Builder metadata(Map<String, String> v) { this.metadata = v; return this; }
        public Builder tenancyId(String v) { this.tenancyId = v; return this; }

        public AgentChannelBinding build() {
            return new AgentChannelBinding(id, channelId, agentInstanceId, backendKey,
                    agentBriefing, mcpServers, persistent, maxConcurrency,
                    contextWindowSize, metadata, tenancyId);
        }
    }
}
