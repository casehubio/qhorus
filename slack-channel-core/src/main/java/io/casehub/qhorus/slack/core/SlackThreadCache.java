package io.casehub.qhorus.slack.core;

import java.time.Instant;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "slack_thread_cache")
public class SlackThreadCache {

    @EmbeddedId
    public SlackThreadCacheId id;

    public String threadTs;

    public Instant createdAt;
}
