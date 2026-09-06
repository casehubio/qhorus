package io.casehub.qhorus.api;

import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.api.A2AEventBroadcasterBridge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

class A2AEventBroadcasterBridgeTest {

    @Test
    void publish_whenEnabled_broadcastsToTopic() {
        List<String> published = new ArrayList<>();
        BiConsumer<String, String> broadcaster = (topic, data) -> published.add(topic + "|" + data);

        var bridge = new A2AEventBroadcasterBridge(true, broadcaster);
        var msg = outbound("task-123", MessageType.STATUS, "working on it");

        bridge.publish(msg);

        assertThat(published).hasSize(1);
        assertThat(published.getFirst()).startsWith("a2a.task.task-123|");
        assertThat(published.getFirst()).contains("\"state\":");
    }

    @Test
    void publish_whenDisabled_noop() {
        List<String> published = new ArrayList<>();
        BiConsumer<String, String> broadcaster = (topic, data) -> published.add(data);

        var bridge = new A2AEventBroadcasterBridge(false, broadcaster);
        bridge.publish(outbound("task-123", MessageType.DONE, "finished"));

        assertThat(published).isEmpty();
    }

    @Test
    void publish_nullCorrelationId_skipped() {
        List<String> published = new ArrayList<>();
        BiConsumer<String, String> broadcaster = (topic, data) -> published.add(data);

        var bridge = new A2AEventBroadcasterBridge(true, broadcaster);
        bridge.publish(outbound(null, MessageType.EVENT, null));

        assertThat(published).isEmpty();
    }

    @Test
    void publish_terminalType_includesFinalTrue() {
        List<String> published = new ArrayList<>();
        BiConsumer<String, String> broadcaster = (topic, data) -> published.add(data);

        var bridge = new A2AEventBroadcasterBridge(true, broadcaster);
        bridge.publish(outbound("task-456", MessageType.DONE, "completed"));

        assertThat(published).hasSize(1);
        assertThat(published.getFirst()).contains("\"final\":true");
    }

    @Test
    void publish_nonTerminalType_includesFinalFalse() {
        List<String> published = new ArrayList<>();
        BiConsumer<String, String> broadcaster = (topic, data) -> published.add(data);

        var bridge = new A2AEventBroadcasterBridge(true, broadcaster);
        bridge.publish(outbound("task-789", MessageType.STATUS, "progress"));

        assertThat(published).hasSize(1);
        assertThat(published.getFirst()).contains("\"final\":false");
    }

    private static OutboundMessage outbound(String correlationId, MessageType type, String content) {
        return new OutboundMessage(
                UUID.randomUUID(), "sender", type, content, correlationId,
                null, null, null, null);
    }
}
