package io.casehub.qhorus.api.spi.channels;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelPage;
import io.casehub.qhorus.api.channel.ChannelQuery;
import io.casehub.qhorus.api.message.Message;

import java.util.List;
import java.util.UUID;

@McpDomain("channels")
public interface ChannelsApi {

    @PlatformQuery("List channels matching filter criteria")
    ChannelPage channels(ChannelQuery query);

    @PlatformQuery("Get a channel by ID or name")
    Channel channel(UUID id, String name);

    @PlatformQuery("Get messages in a channel")
    List<Message> channelMessages(UUID channelId, Long afterId, Integer limit);

    @PlatformMutation("Create a new channel")
    Channel createChannel(ChannelCreateRequest input);

    @PlatformMutation("Delete a channel")
    long deleteChannel(UUID channelId, Boolean force);

    @PlatformMutation("Pause a channel")
    Channel pauseChannel(UUID channelId);

    @PlatformMutation("Resume a paused channel")
    Channel resumeChannel(UUID channelId);
}
