package io.casehub.qhorus.slack;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Embeddable;

@Embeddable
public class SlackThreadCacheId implements Serializable {

    public UUID channelId;
    public String correlationId;

    public SlackThreadCacheId() {}

    public SlackThreadCacheId(UUID channelId, String correlationId) {
        this.channelId = channelId;
        this.correlationId = correlationId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SlackThreadCacheId other)) return false;
        return channelId.equals(other.channelId) && correlationId.equals(other.correlationId);
    }

    @Override
    public int hashCode() {
        return 31 * channelId.hashCode() + correlationId.hashCode();
    }
}
