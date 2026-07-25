package io.casehub.qhorus.runtime.channel;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.casehub.qhorus.api.channel.Presence;
import io.casehub.qhorus.api.channel.PresenceTracker;
import io.casehub.qhorus.api.channel.PresenceStatus;
import io.casehub.qhorus.runtime.config.PresenceConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PresenceService implements PresenceTracker {

    private final Cache<String, PresenceEntry> cache;
    private final PresenceConfig config;
    private final Clock clock;

    record PresenceEntry(PresenceStatus reportedStatus, java.time.Instant lastSeenAt, String statusMessage) {}

    @Inject
    public PresenceService(PresenceConfig config, Clock clock) {
        if (config.awayTimeout().compareTo(config.offlineTimeout()) >= 0) {
            throw new IllegalStateException(
                    "awayTimeout (" + config.awayTimeout() + ") must be less than offlineTimeout (" + config.offlineTimeout() + ")");
        }
        this.config = config;
        this.clock = clock;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(config.offlineTimeout())
                .build();
    }

    PresenceService(PresenceService previous, PresenceConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
        this.cache = previous.cache;
    }

    public void heartbeat(String memberId, PresenceStatus status, String statusMessage) {
        if (!status.isReportable()) {
            throw new IllegalArgumentException(
                    "Only reportable statuses (ONLINE, AVAILABLE, BUSY) are accepted; got " + status);
        }
        cache.put(memberId, new PresenceEntry(status, clock.instant(), statusMessage));
    }

    public Presence getPresence(String memberId) {
        PresenceEntry entry = cache.getIfPresent(memberId);
        if (entry == null) {
            return new Presence(memberId, PresenceStatus.OFFLINE, PresenceStatus.OFFLINE, null, null);
        }
        PresenceStatus effective = computeEffectiveStatus(entry);
        return new Presence(memberId, effective, entry.reportedStatus(), entry.lastSeenAt(), entry.statusMessage());
    }

    public List<Presence> getChannelPresence(UUID channelId) {
        return membershipService.listMembers(channelId).stream()
                .map(m -> getPresence(m.memberId()))
                .toList();
    }

    public void setOffline(String memberId) {
        cache.invalidate(memberId);
    }

    private PresenceStatus computeEffectiveStatus(PresenceEntry entry) {
        Duration elapsed = Duration.between(entry.lastSeenAt(), clock.instant());
        if (elapsed.compareTo(config.awayTimeout()) >= 0) {
            return PresenceStatus.AWAY;
        }
        return entry.reportedStatus();
    }

    @Inject
    public ChannelMembershipService membershipService;
    @jakarta.inject.Inject
    io.casehub.platform.api.identity.CurrentPrincipal currentPrincipal;

    @Override
    public void heartbeat(PresenceStatus status, String statusMessage) {
        heartbeat(currentPrincipal.actorId(), status, statusMessage);
    }

    @Override
    public void setOffline() {
        setOffline(currentPrincipal.actorId());
    }

}
