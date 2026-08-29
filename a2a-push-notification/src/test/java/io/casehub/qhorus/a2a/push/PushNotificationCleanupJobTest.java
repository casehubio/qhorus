package io.casehub.qhorus.a2a.push;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.a2a.PushNotificationConfig;
import io.casehub.qhorus.persistence.memory.InMemoryPushNotificationConfigStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PushNotificationCleanupJobTest {

    private InMemoryPushNotificationConfigStore store;
    private PushConfig config;
    private PushNotificationCleanupJob job;
    private Instant now;

    @BeforeEach
    void setUp() {
        store = new InMemoryPushNotificationConfigStore();
        config = mock(PushConfig.class);
        when(config.enabled()).thenReturn(true);
        when(config.ttlThreshold()).thenReturn(Duration.ofHours(24));
        now = Instant.now();
        job = new PushNotificationCleanupJob(store, config, Clock.fixed(now, ZoneOffset.UTC));
    }

    private PushNotificationConfig cfg(String taskId, Instant createdAt, Instant lastPushedAt) {
        return new PushNotificationConfig(UUID.randomUUID(), taskId, UUID.randomUUID(),
                "https://push.example.com", null, null, null, "default", createdAt, lastPushedAt);
    }

    @Test
    void cleanup_removesExpiredConfigs() {
        store.put(cfg("old-task", now.minus(48, ChronoUnit.HOURS), null));
        job.cleanup();
        assertThat(store.activeTaskIds()).isEmpty();
    }

    @Test
    void cleanup_preservesFreshConfigs() {
        PushNotificationConfig fresh = cfg("fresh-task", now.minus(1, ChronoUnit.HOURS), null);
        store.put(fresh);
        job.cleanup();
        assertThat(store.findByTaskId("fresh-task")).hasSize(1);
    }

    @Test
    void cleanup_preservesActiveConfigs_withRecentPush() {
        store.put(cfg("active-task", now.minus(48, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.HOURS)));
        job.cleanup();
        assertThat(store.findByTaskId("active-task")).hasSize(1);
    }

    @Test
    void cleanup_usesCoalesceLogic() {
        store.put(cfg("old-created-recent-push",
                now.minus(72, ChronoUnit.HOURS),
                now.minus(2, ChronoUnit.HOURS)));
        store.put(cfg("old-created-no-push",
                now.minus(72, ChronoUnit.HOURS), null));

        job.cleanup();

        assertThat(store.findByTaskId("old-created-recent-push")).hasSize(1);
        assertThat(store.findByTaskId("old-created-no-push")).isEmpty();
    }

    @Test
    void cleanup_disabled_doesNothing() {
        when(config.enabled()).thenReturn(false);
        store.put(cfg("should-survive", now.minus(48, ChronoUnit.HOURS), null));
        job.cleanup();
        assertThat(store.findByTaskId("should-survive")).hasSize(1);
    }
}
