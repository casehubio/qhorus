package io.casehub.qhorus.api.channel;

import java.util.List;
import java.util.UUID;

public interface PresenceTracker {

    void heartbeat(PresenceStatus status, String statusMessage);

    Presence getPresence(String memberId);

    List<Presence> getChannelPresence(UUID channelId);

    void setOffline();
}
