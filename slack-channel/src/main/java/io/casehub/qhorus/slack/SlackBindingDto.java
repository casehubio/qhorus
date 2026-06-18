package io.casehub.qhorus.slack;

import java.util.UUID;

/** Response body for GET /slack-channel/bindings/{channelId}. Token is never included. */
public record SlackBindingDto(UUID qhorusChannelId, String slackChannelId, String workspaceId) {

    static SlackBindingDto from(UUID channelId, SlackBotBinding b) {
        return new SlackBindingDto(channelId, b.slackChannelId, b.workspaceId);
    }
}
