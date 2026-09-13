package io.casehub.qhorus.runtime.channel;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.casehub.qhorus.api.channel.Presence;
import io.casehub.qhorus.api.channel.PresenceChangedEvent;
import io.casehub.qhorus.api.channel.PresenceTracker;
import io.casehub.qhorus.api.channel.PresenceStatus;
import io.casehub.qhorus.runtime.config.PresenceConfig;
import io.casehub.platform.api.identity.CurrentPrincipal;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class PresenceService implements PresenceTracker {

    private final Cache<String, PresenceEntry> cache;
    private final PresenceConfig config;
    private final Clock clock;
    private final ChannelMembershipService membershipService;
    private final CurrentPrincipal currentPrincipal;
    private final Consumer<PresenceChangedEvent> presenceConsumer;

    record PresenceEntry(PresenceStatus reportedStatus, java.time.Instant lastSeenAt, String statusMessage) {}

    public PresenceService(PresenceConfig config, Clock clock,
                           ChannelMembershipService membershipService,
                           CurrentPrincipal currentPrincipal,
                           Consumer<PresenceChangedEvent> presenceConsumer) {
        if (config.awayTimeout().compareTo(config.offlineTimeout()) >= 0) {
            throw new IllegalStateException(
                    "awayTimeout (" + config.awayTimeout() + ") must be less than offlineTimeout (" + config.offlineTimeout() + ")");
        }
        this.config = config;
        this.clock = clock;
        this.membershipService = membershipService;
        this.currentPrincipal = currentPrincipal;
        this.presenceConsumer = presenceConsumer;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(config.offlineTimeout())
                .build();
    }

    PresenceService(PresenceService previous, PresenceConfig config, Clock clock,
                    ChannelMembershipService membershipService,
                    CurrentPrincipal currentPrincipal,
                    Consumer<PresenceChangedEvent> presenceConsumer) {
        this.config = config;
        this.clock = clock;
        this.membershipService = membershipService;
        this.currentPrincipal = currentPrincipal;
        this.presenceConsumer = presenceConsumer;
        this.cache = previous.cache;
    }

    public void heartbeat(String memberId, PresenceStatus status, String statusMessage) {
        if (!status.isReportable()) {
            throw new IllegalArgumentException(
                    "Only reportable statuses (ONLINE, AVAILABLE, BUSY) are accepted; got " + status);
        }
        PresenceEntry previous = cache.getIfPresent(memberId);
        PresenceStatus previousStatus = previous != null ? previous.reportedStatus() : PresenceStatus.OFFLINE;
        cache.put(memberId, new PresenceEntry(status, clock.instant(), statusMessage));
        if (presenceConsumer != null && previousStatus != status) {
            presenceConsumer.accept(new PresenceChangedEvent(
                    memberId, null, status, previousStatus, clock.instant()));
        }
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
        PresenceEntry previous = cache.getIfPresent(memberId);
        cache.invalidate(memberId);
        if (presenceConsumer != null && previous != null) {
            presenceConsumer.accept(new PresenceChangedEvent(
                    memberId, null, PresenceStatus.OFFLINE, previous.reportedStatus(), clock.instant()));
        }
    }

    private PresenceStatus computeEffectiveStatus(PresenceEntry entry) {
        Duration elapsed = Duration.between(entry.lastSeenAt(), clock.instant());
        if (elapsed.compareTo(config.awayTimeout()) >= 0) {
            return PresenceStatus.AWAY;
        }
        return entry.reportedStatus();
    }

    @Override
    public void heartbeat(PresenceStatus status, String statusMessage) {
        heartbeat(currentPrincipal.actorId(), status, statusMessage);
    }

    @Override
    public void setOffline() {
        setOffline(currentPrincipal.actorId());
    }
}
