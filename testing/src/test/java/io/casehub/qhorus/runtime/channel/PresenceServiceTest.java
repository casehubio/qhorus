package io.casehub.qhorus.runtime.channel;

import io.casehub.qhorus.api.channel.Presence;
import io.casehub.qhorus.api.channel.PresenceStatus;
import io.casehub.qhorus.runtime.config.PresenceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

class PresenceServiceTest {

    private PresenceService service;
    private Instant now = Instant.parse("2026-07-12T12:00:00Z");
    private final PresenceConfig config = new PresenceConfig() {
        public Duration awayTimeout() { return Duration.ofMinutes(2); }
        public Duration offlineTimeout() { return Duration.ofMinutes(10); }
        public Duration heartbeatInterval() { return Duration.ofSeconds(30); }
    };

    @BeforeEach
    void setUp() {
        service = new PresenceService(config, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void advanceTime(Duration d) {
        now = now.plus(d);
        service = new PresenceService(service, config, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void heartbeatSetsPresence() {
        service.heartbeat("agent-1", PresenceStatus.ONLINE, null);
        Presence p = service.getPresence("agent-1");
        assertThat(p.status()).isEqualTo(PresenceStatus.ONLINE);
        assertThat(p.reportedStatus()).isEqualTo(PresenceStatus.ONLINE);
        assertThat(p.lastSeenAt()).isEqualTo(now);
    }

    @Test
    void heartbeatWithStatusMessage() {
        service.heartbeat("agent-1", PresenceStatus.BUSY, "Processing case-456");
        Presence p = service.getPresence("agent-1");
        assertThat(p.status()).isEqualTo(PresenceStatus.BUSY);
        assertThat(p.statusMessage()).isEqualTo("Processing case-456");
    }

    @Test
    void unknownMemberReturnsOffline() {
        Presence p = service.getPresence("unknown");
        assertThat(p.status()).isEqualTo(PresenceStatus.OFFLINE);
        assertThat(p.reportedStatus()).isEqualTo(PresenceStatus.OFFLINE);
        assertThat(p.lastSeenAt()).isNull();
    }

    @Test
    void awayAfterTimeout() {
        service.heartbeat("agent-1", PresenceStatus.ONLINE, null);
        advanceTime(Duration.ofMinutes(3));
        Presence p = service.getPresence("agent-1");
        assertThat(p.status()).isEqualTo(PresenceStatus.AWAY);
        assertThat(p.reportedStatus()).isEqualTo(PresenceStatus.ONLINE);
    }

    @Test
    void setOfflineImmediately() {
        service.heartbeat("agent-1", PresenceStatus.ONLINE, null);
        service.setOffline("agent-1");
        Presence p = service.getPresence("agent-1");
        assertThat(p.status()).isEqualTo(PresenceStatus.OFFLINE);
    }

    @Test
    void heartbeatWithAwayRejected() {
        assertThatThrownBy(() -> service.heartbeat("agent-1", PresenceStatus.AWAY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reportable");
    }

    @Test
    void heartbeatWithOfflineRejected() {
        assertThatThrownBy(() -> service.heartbeat("agent-1", PresenceStatus.OFFLINE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reportable");
    }

    @Test
    void configInvariantViolation() {
        var badConfig = new PresenceConfig() {
            public Duration awayTimeout() { return Duration.ofMinutes(15); }
            public Duration offlineTimeout() { return Duration.ofMinutes(10); }
            public Duration heartbeatInterval() { return Duration.ofSeconds(30); }
        };
        assertThatThrownBy(() -> new PresenceService(badConfig, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("awayTimeout");
    }

    @Test
    void heartbeatResetsAwayBackToReported() {
        service.heartbeat("agent-1", PresenceStatus.AVAILABLE, null);
        advanceTime(Duration.ofMinutes(3));
        assertThat(service.getPresence("agent-1").status()).isEqualTo(PresenceStatus.AWAY);

        service.heartbeat("agent-1", PresenceStatus.AVAILABLE, null);
        Presence p = service.getPresence("agent-1");
        assertThat(p.status()).isEqualTo(PresenceStatus.AVAILABLE);
    }

    @Test
    void withinAwayTimeoutReturnsReportedStatus() {
        service.heartbeat("agent-1", PresenceStatus.BUSY, "Working");
        advanceTime(Duration.ofSeconds(90));
        Presence p = service.getPresence("agent-1");
        assertThat(p.status()).isEqualTo(PresenceStatus.BUSY);
    }
}
