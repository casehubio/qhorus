package io.casehub.qhorus.graphql.channels;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.ModelEnricher;
import io.casehub.qhorus.api.channel.ChannelReader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;

@McpDomain("channels")
@ApplicationScoped
public class ChannelsModelEnricher implements ModelEnricher {

    private final ChannelReader channelReader;

    @Inject
    public ChannelsModelEnricher(ChannelReader channelReader) {
        this.channelReader = channelReader;
    }

    @Override
    public String summary() {
        return "Communication channels — create, query, pause, resume, delete channels. "
                + "Retrieve message history. Subscribe to live channel activity and presence.";
    }

    @Override
    public Map<String, Object> state() {
        int count = channelReader.listAll().size();
        return Map.of("activeChannels", count);
    }
}
