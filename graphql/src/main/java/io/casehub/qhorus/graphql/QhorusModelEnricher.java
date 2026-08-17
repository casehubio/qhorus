package io.casehub.qhorus.graphql;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.ModelEnricher;
import io.casehub.qhorus.api.channel.ChannelReader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;

@McpDomain("qhorus")
@ApplicationScoped
public class QhorusModelEnricher implements ModelEnricher {

    private final ChannelReader channelReader;

    @Inject
    public QhorusModelEnricher(ChannelReader channelReader) {
        this.channelReader = channelReader;
    }

    @Override
    public String summary() {
        return "Agent communication mesh — create, pause, resume, delete channels. "
                + "Dispatch typed messages, query message history, track commitments. "
                + "Subscribe to live channel activity and presence changes.";
    }

    @Override
    public Map<String, Object> state() {
        int count = channelReader.listAll().size();
        return Map.of("activeChannels", count);
    }
}
