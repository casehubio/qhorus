package io.casehub.qhorus.slack;

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

    /** Slack channel ID, e.g. "C123ABC". */
    public String slackChannelId;

    /** Slack workspace/team ID, e.g. "T123ABC". Also used as the MicroProfile Config credential key. */
    public String workspaceId;

    public Instant createdAt;
}
