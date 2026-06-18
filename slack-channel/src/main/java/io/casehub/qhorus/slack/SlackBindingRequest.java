package io.casehub.qhorus.slack;

/** Request body for PUT /slack-channel/bindings/{channelId}. */
public record SlackBindingRequest(String slackChannelId, String workspaceId) {}
