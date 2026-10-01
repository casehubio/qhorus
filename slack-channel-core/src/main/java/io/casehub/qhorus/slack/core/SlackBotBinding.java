package io.casehub.qhorus.slack.core;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "slack_bot_binding")
public class SlackBotBinding {

    @Id
    public UUID channelId;

    public String slackChannelId;

    public String workspaceId;

    public Instant createdAt;
}
