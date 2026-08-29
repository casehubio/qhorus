package io.casehub.qhorus.a2a.push;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.a2a.PushNotificationConfig;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.persistence.memory.InMemoryPushNotificationConfigStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushNotificationBackendTest {

    private InMemoryPushNotificationConfigStore store;
    private PushNotificationPoster poster;
    private PushNotificationBackend backend;
    private UUID channelId;
    private ChannelRef channelRef;

    @BeforeEach
    void setUp() {
        store = new InMemoryPushNotificationConfigStore();
        poster = mock(PushNotificationPoster.class);
        backend = new PushNotificationBackend(store, poster);
        backend.maxUrlFailures = 3;
        channelId = UUID.randomUUID();
        channelRef = new ChannelRef(channelId, "test-channel");
    }

    private PushNotificationConfig config(String taskId, String url) {
        return new PushNotificationConfig(UUID.randomUUID(), taskId, channelId, url,
                null, null, null, "default", Instant.now(), null);
    }

    private OutboundMessage message(MessageType type, String correlationId, String content) {
        return new OutboundMessage(
                null, "sender-1", type, content, correlationId, null, null, null, null);
    }

    @Test
    void post_nonPushRelevantType_noOp() {
        PushNotificationConfig cfg = config("task-1", "https://a.com/push");
        store.put(cfg);
        backend.onConfigCreated(channelId, "task-1");

        backend.post(channelRef, message(MessageType.EVENT, "task-1", null));
        backend.post(channelRef, message(MessageType.COMMAND, "task-1", null));
        backend.post(channelRef, message(MessageType.QUERY, "task-1", null));
        backend.post(channelRef, message(MessageType.PROPOSE, "task-1", null));

        verify(poster, never()).push(any(), any(), any(), any());
    }

    @Test
    void post_pushRelevantType_noCorrIdMatch_noOp() {
        backend.post(channelRef, message(MessageType.STATUS, "unknown-task", null));
        verify(poster, never()).push(any(), any(), any(), any());
    }

    @Test
    void post_status_matchingCorrId_pushesWorkingState() {
        String taskId = "task-status-" + UUID.randomUUID();
        PushNotificationConfig cfg = config(taskId, "https://push.example.com");
        store.put(cfg);
        backend.onConfigCreated(channelId, taskId);

        when(poster.push(any(), eq("working"), eq("doing stuff"), eq(channelId)))
                .thenReturn(PushPostResult.ok(200));

        backend.post(channelRef, message(MessageType.STATUS, taskId, "doing stuff"));
        verify(poster).push(any(), eq("working"), eq("doing stuff"), eq(channelId));
    }

    @Test
    void post_done_pushesCompleted_deletesConfig() {
        String taskId = "task-done-" + UUID.randomUUID();
        PushNotificationConfig cfg = config(taskId, "https://push.example.com");
        store.put(cfg);
        backend.onConfigCreated(channelId, taskId);

        when(poster.push(any(), eq("completed"), any(), any()))
                .thenReturn(PushPostResult.ok(200));

        backend.post(channelRef, message(MessageType.DONE, taskId, "finished"));

        assertThat(store.findByTaskId(taskId)).isEmpty();
    }

    @Test
    void post_failure_pushesFailed_deletesConfig() {
        String taskId = "task-fail-" + UUID.randomUUID();
        PushNotificationConfig cfg = config(taskId, "https://push.example.com");
        store.put(cfg);
        backend.onConfigCreated(channelId, taskId);

        when(poster.push(any(), eq("failed"), any(), any()))
                .thenReturn(PushPostResult.ok(200));

        backend.post(channelRef, message(MessageType.FAILURE, taskId, "error"));

        assertThat(store.findByTaskId(taskId)).isEmpty();
    }

    @Test
    void post_decline_pushesCanceled_deletesConfig() {
        String taskId = "task-decline-" + UUID.randomUUID();
        PushNotificationConfig cfg = config(taskId, "https://push.example.com");
        store.put(cfg);
        backend.onConfigCreated(channelId, taskId);

        when(poster.push(any(), eq("canceled"), any(), any()))
                .thenReturn(PushPostResult.ok(200));

        backend.post(channelRef, message(MessageType.DECLINE, taskId, null));

        assertThat(store.findByTaskId(taskId)).isEmpty();
    }

    @Test
    void post_handoff_pushesWorking() {
        String taskId = "task-ho-" + UUID.randomUUID();
        PushNotificationConfig cfg = config(taskId, "https://push.example.com");
        store.put(cfg);
        backend.onConfigCreated(channelId, taskId);

        when(poster.push(any(), eq("working"), any(), any()))
                .thenReturn(PushPostResult.ok(200));

        backend.post(channelRef, message(MessageType.HANDOFF, taskId, null));
        verify(poster).push(any(), eq("working"), any(), eq(channelId));

        assertThat(store.findByTaskId(taskId)).hasSize(1);
    }

    @Test
    void post_httpFailure_nonTerminal_dropsMessage() {
        String taskId = "task-drop-" + UUID.randomUUID();
        PushNotificationConfig cfg = config(taskId, "https://push.example.com");
        store.put(cfg);
        backend.onConfigCreated(channelId, taskId);

        when(poster.push(any(), any(), any(), any()))
                .thenReturn(PushPostResult.fail(500, "server error"));

        backend.post(channelRef, message(MessageType.STATUS, taskId, "update"));

        assertThat(store.findByTaskId(taskId)).hasSize(1);
    }

    @Test
    void post_httpFailure_terminal_tracksPending() {
        String taskId = "task-pending-" + UUID.randomUUID();
        PushNotificationConfig cfg = config(taskId, "https://fail.example.com");
        store.put(cfg);
        backend.onConfigCreated(channelId, taskId);

        when(poster.push(any(), eq("completed"), any(), any()))
                .thenReturn(PushPostResult.fail(500, "down"))
                .thenReturn(PushPostResult.ok(200));

        backend.post(channelRef, message(MessageType.DONE, taskId, "done"));
        assertThat(store.findByTaskId(taskId)).hasSize(1);

        backend.clock = Clock.fixed(Instant.now().plus(Duration.ofSeconds(10)), ZoneOffset.UTC);
        backend.post(channelRef, message(MessageType.STATUS, taskId, "retry trigger"));
        assertThat(store.findByTaskId(taskId)).isEmpty();
    }

    @Test
    void post_neverThrows_onPosterException() {
        String taskId = "task-exc-" + UUID.randomUUID();
        PushNotificationConfig cfg = config(taskId, "https://push.example.com");
        store.put(cfg);
        backend.onConfigCreated(channelId, taskId);

        when(poster.push(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        backend.post(channelRef, message(MessageType.STATUS, taskId, "test"));
    }

    @Test
    void post_urlExhausted_configDeleted() {
        String taskId = "task-exhaust-" + UUID.randomUUID();
        PushNotificationConfig cfg = config(taskId, "https://dead.example.com");
        store.put(cfg);
        backend.onConfigCreated(channelId, taskId);

        when(poster.push(any(), any(), any(), any()))
                .thenReturn(PushPostResult.fail(500, "dead"));

        Instant base = Instant.now();
        for (int i = 0; i < 3; i++) {
            backend.clock = Clock.fixed(base.plus(Duration.ofHours(i)), ZoneOffset.UTC);
            backend.post(channelRef, message(MessageType.STATUS, taskId, "attempt " + i));
        }

        assertThat(store.findByTaskId(taskId)).isEmpty();
    }

    @Test
    void post_urlInBackoff_skipped() {
        String taskId = "task-backoff-" + UUID.randomUUID();
        PushNotificationConfig cfg = config(taskId, "https://slow.example.com");
        store.put(cfg);
        backend.onConfigCreated(channelId, taskId);

        when(poster.push(any(), any(), any(), any()))
                .thenReturn(PushPostResult.fail(500, "slow"));

        backend.post(channelRef, message(MessageType.STATUS, taskId, "first"));

        backend.post(channelRef, message(MessageType.STATUS, taskId, "second"));

        verify(poster, times(1)).push(any(), any(), any(), any());
    }

    @Test
    void post_lazyDbFallback_onCacheMiss() {
        String taskId = "task-lazy-" + UUID.randomUUID();
        PushNotificationConfig cfg = config(taskId, "https://push.example.com");
        store.put(cfg);

        when(poster.push(any(), eq("working"), any(), any()))
                .thenReturn(PushPostResult.ok(200));

        backend.post(channelRef, message(MessageType.STATUS, taskId, "found via DB"));
        verify(poster).push(any(), eq("working"), eq("found via DB"), eq(channelId));
    }

    @Test
    void backendId() {
        assertThat(backend.backendId()).isEqualTo("a2a-push");
    }

    @Test
    void deliveryGuarantee_atLeastOnce() {
        assertThat(backend.deliveryGuarantee())
                .isEqualTo(io.casehub.qhorus.api.gateway.DeliveryGuarantee.AT_LEAST_ONCE);
    }
}
