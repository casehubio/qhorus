package io.casehub.qhorus.playbook.bridge;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

public class ProtocolDeadlineScheduler {

    private static final Logger LOG = Logger.getLogger(ProtocolDeadlineScheduler.class);

    private final ConcurrentHashMap<DeadlineKey, Instant> deadlines = new ConcurrentHashMap<>();
    private final Consumer<DeadlineKey> firer;

    Clock clock = Clock.systemUTC();

    public ProtocolDeadlineScheduler(Consumer<DeadlineKey> firer) {
        this.firer = firer;
    }

    public void register(DeadlineKey key, Instant firesAt) {
        deadlines.put(key, firesAt);
        LOG.debugf("Registered deadline '%s' for %s/%s at %s",
                key.deadlineName(), key.channelId(), key.protocolName(), firesAt);
    }

    public void cancel(DeadlineKey key) {
        deadlines.remove(key);
    }

    public void clear(UUID channelId, String protocolName) {
        deadlines.keySet().removeIf(k ->
                k.channelId().equals(channelId) && k.protocolName().equals(protocolName));
    }

    public void clearAll(UUID channelId) {
        deadlines.keySet().removeIf(k -> k.channelId().equals(channelId));
    }

    public int activeCount() {
        return deadlines.size();
    }

    public void sweep() {
        Instant now = clock.instant();
        Iterator<Map.Entry<DeadlineKey, Instant>> it = deadlines.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<DeadlineKey, Instant> entry = it.next();
            if (!entry.getValue().isAfter(now)) {
                DeadlineKey key = entry.getKey();
                it.remove();
                try {
                    firer.accept(key);
                    LOG.infof("Fired deadline event '%s' for %s/%s",
                            key.deadlineName(), key.channelId(), key.protocolName());
                } catch (Exception e) {
                    LOG.warnf(e, "Failed to fire deadline event '%s' for %s/%s",
                            key.deadlineName(), key.channelId(), key.protocolName());
                }
            }
        }
    }
}
