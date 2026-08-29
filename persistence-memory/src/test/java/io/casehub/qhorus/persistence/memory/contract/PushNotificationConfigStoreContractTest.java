package io.casehub.qhorus.persistence.memory.contract;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.a2a.PushNotificationConfig;
import io.casehub.qhorus.api.store.CrossTenantPushNotificationConfigStore;
import io.casehub.qhorus.api.store.PushNotificationConfigStore;

import static org.assertj.core.api.Assertions.assertThat;

abstract class PushNotificationConfigStoreContractTest {

    protected abstract PushNotificationConfigStore store();
    protected abstract CrossTenantPushNotificationConfigStore crossTenantStore();

    private PushNotificationConfig config(UUID id, String taskId, UUID channelId, String url) {
        return new PushNotificationConfig(id, taskId, channelId, url, null, null, null,
                "default", Instant.now(), null);
    }

    @Test
    void put_and_findById() {
        UUID id = UUID.randomUUID();
        PushNotificationConfig cfg = config(id, "task-1", UUID.randomUUID(), "https://push.example.com/cb");
        store().put(cfg);
        Optional<PushNotificationConfig> found = store().findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().taskId()).isEqualTo("task-1");
        assertThat(found.get().url()).isEqualTo("https://push.example.com/cb");
    }

    @Test
    void findById_notFound_returnsEmpty() {
        assertThat(store().findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void put_and_findByTaskId() {
        String taskId = "task-" + UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        store().put(config(UUID.randomUUID(), taskId, channelId, "https://a.com/push"));
        store().put(config(UUID.randomUUID(), taskId, channelId, "https://b.com/push"));
        List<PushNotificationConfig> found = store().findByTaskId(taskId);
        assertThat(found).hasSize(2);
    }

    @Test
    void put_duplicateTaskIdUrl_overwrites() {
        String taskId = "task-dup-" + UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        store().put(config(id1, taskId, channelId, "https://same.com/push"));
        store().put(config(id2, taskId, channelId, "https://same.com/push"));
        List<PushNotificationConfig> found = store().findByTaskId(taskId);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).id()).isEqualTo(id2);
    }

    @Test
    void delete_byId() {
        UUID id = UUID.randomUUID();
        store().put(config(id, "task-del", UUID.randomUUID(), "https://del.com/push"));
        store().delete(id);
        assertThat(store().findById(id)).isEmpty();
    }

    @Test
    void deleteByTaskId() {
        String taskId = "task-delall-" + UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        store().put(config(UUID.randomUUID(), taskId, channelId, "https://a.com/push"));
        store().put(config(UUID.randomUUID(), taskId, channelId, "https://b.com/push"));
        store().deleteByTaskId(taskId);
        assertThat(store().findByTaskId(taskId)).isEmpty();
    }

    @Test
    void activeTaskIds_returnsDistinctTaskIds() {
        String t1 = "task-active-" + UUID.randomUUID();
        String t2 = "task-active-" + UUID.randomUUID();
        UUID ch = UUID.randomUUID();
        store().put(config(UUID.randomUUID(), t1, ch, "https://a.com/push"));
        store().put(config(UUID.randomUUID(), t1, ch, "https://b.com/push"));
        store().put(config(UUID.randomUUID(), t2, ch, "https://c.com/push"));
        Set<String> ids = crossTenantStore().activeTaskIds();
        assertThat(ids).contains(t1, t2);
    }

    @Test
    void findExpired_withThreshold() {
        Instant old = Instant.now().minus(48, ChronoUnit.HOURS);
        Instant recent = Instant.now();
        UUID ch = UUID.randomUUID();
        UUID oldId = UUID.randomUUID();
        store().put(new PushNotificationConfig(oldId, "task-old", ch,
                "https://old.com/push", null, null, null, "default", old, null));
        store().put(new PushNotificationConfig(UUID.randomUUID(), "task-new", ch,
                "https://new.com/push", null, null, null, "default", recent, null));

        Instant threshold = Instant.now().minus(24, ChronoUnit.HOURS);
        List<PushNotificationConfig> expired = crossTenantStore().findExpired(threshold);
        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).id()).isEqualTo(oldId);
    }

    @Test
    void findExpired_usesLastPushedAtWhenPresent() {
        Instant oldCreated = Instant.now().minus(48, ChronoUnit.HOURS);
        Instant recentPush = Instant.now().minus(1, ChronoUnit.HOURS);
        UUID ch = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        store().put(new PushNotificationConfig(id, "task-active-push", ch,
                "https://active.com/push", null, null, null, "default", oldCreated, recentPush));

        Instant threshold = Instant.now().minus(24, ChronoUnit.HOURS);
        List<PushNotificationConfig> expired = crossTenantStore().findExpired(threshold);
        assertThat(expired).isEmpty();
    }

    @Test
    void updateLastPushedAt() {
        UUID id = UUID.randomUUID();
        store().put(config(id, "task-update", UUID.randomUUID(), "https://upd.com/push"));
        Instant now = Instant.now();
        crossTenantStore().updateLastPushedAt(id, now);
        Optional<PushNotificationConfig> found = store().findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().lastPushedAt()).isEqualTo(now);
    }

    @Test
    void findByChannelId() {
        UUID ch1 = UUID.randomUUID();
        UUID ch2 = UUID.randomUUID();
        store().put(config(UUID.randomUUID(), "t1", ch1, "https://a.com/push"));
        store().put(config(UUID.randomUUID(), "t2", ch1, "https://b.com/push"));
        store().put(config(UUID.randomUUID(), "t3", ch2, "https://c.com/push"));
        List<PushNotificationConfig> found = crossTenantStore().findByChannelId(ch1);
        assertThat(found).hasSize(2);
    }
}
