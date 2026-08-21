package io.casehub.qhorus.api.channel;

import java.util.Map;
import java.util.UUID;

public interface UnreadCountProvider {
    Map<UUID, UnreadCount> getUnreadCounts(String memberId, String tenancyId);
}
