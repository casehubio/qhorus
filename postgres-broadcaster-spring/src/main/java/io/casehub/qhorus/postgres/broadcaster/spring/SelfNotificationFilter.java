package io.casehub.qhorus.postgres.broadcaster.spring;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

final class SelfNotificationFilter {

    private final Set<Long> recentIds;
    private final int maxSize;

    SelfNotificationFilter(int maxSize) {
        this.maxSize = maxSize;
        this.recentIds = Collections.synchronizedSet(new LinkedHashSet<>());
    }

    void recordSent(Long messageId) {
        synchronized (recentIds) {
            recentIds.add(messageId);
            if (recentIds.size() > maxSize) {
                Iterator<Long> it = recentIds.iterator();
                it.next();
                it.remove();
            }
        }
    }

    boolean wasSentLocally(Long messageId) {
        return recentIds.contains(messageId);
    }
}
