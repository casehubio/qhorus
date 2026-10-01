package io.casehub.qhorus.slack.core;

import java.util.UUID;

public record SlackBindingDto(UUID qhorusChannelId, String slackChannelId, String workspaceId) {

    public static SlackBindingDto from(UUID channelId, SlackBotBinding b) {
        return new SlackBindingDto(channelId, b.slackChannelId, b.workspaceId);
    }
}
