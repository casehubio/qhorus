package io.casehub.qhorus.slack;

import java.time.Instant;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "slack_thread_cache")
public class SlackThreadCache {

    @EmbeddedId
    public SlackThreadCacheId id;

    /** Slack root-message timestamp, e.g. "1718567890.123456". Used as thread_ts for replies. */
    public String threadTs;

    public Instant createdAt;
}
