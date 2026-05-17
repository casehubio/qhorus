package io.casehub.qhorus.runtime.message;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;

class MessageObserverDispatcherTest {

    private final UUID channelId = UUID.randomUUID();
    private final String channelName = "test-channel";

    private Message message(MessageType type, String content, String correlationId) {
        final Message m = new Message();
        m.channelId = channelId;
        m.sender = "agent-a";
        m.messageType = type;
        m.content = content;
        m.correlationId = correlationId;
        return m;
    }

    // ── Dispatch coverage ─────────────────────────────────────────────────

    @Test
    void dispatch_commandMessage_notifiesObserver() {
        final List<MessageReceivedEvent> captured = new ArrayList<>();
        final MessageObserver observer = captured::add;

        MessageObserverDispatcher.dispatch(channelName, channelId,
                message(MessageType.COMMAND, "analyse this", "corr-1"),
                List.of(observer));

        assertEquals(1, captured.size());
        final MessageReceivedEvent e = captured.get(0);
        assertEquals(channelName, e.channelName());
        assertEquals(channelId, e.channelId());
        assertEquals(MessageType.COMMAND, e.messageType());
        assertEquals("agent-a", e.senderId());
        assertEquals("corr-1", e.correlationId());
        assertEquals("analyse this", e.content());
    }

    @Test
    void dispatch_multipleObservers_allReceiveEvent() {
        final List<MessageReceivedEvent> first = new ArrayList<>();
        final List<MessageReceivedEvent> second = new ArrayList<>();

        MessageObserverDispatcher.dispatch(channelName, channelId,
                message(MessageType.RESPONSE, "done", null),
                List.of(first::add, second::add));

        assertEquals(1, first.size());
        assertEquals(1, second.size());
    }

    // ── EVENT content nulling ─────────────────────────────────────────────

    @Test
    void dispatch_eventType_contentIsNull() {
        final List<MessageReceivedEvent> captured = new ArrayList<>();

        MessageObserverDispatcher.dispatch(channelName, channelId,
                message(MessageType.EVENT, "{\"tool\":\"search\",\"duration_ms\":42}", null),
                List.of(captured::add));

        assertNull(captured.get(0).content(),
                "EVENT content must be null per PP-20260508-90428f");
    }

    @Test
    void dispatch_nonEventTypes_contentPreserved() {
        for (final MessageType type : MessageType.values()) {
            if (type == MessageType.EVENT) continue;
            final List<MessageReceivedEvent> captured = new ArrayList<>();
            MessageObserverDispatcher.dispatch(channelName, channelId,
                    message(type, "payload-" + type, null),
                    List.of(captured::add));
            assertEquals("payload-" + type, captured.get(0).content(),
                    "content must be preserved for type " + type);
        }
    }

    // ── Fault isolation ───────────────────────────────────────────────────

    @Test
    void dispatch_firstObserverThrows_secondObserverStillFires() {
        final List<MessageReceivedEvent> captured = new ArrayList<>();
        final MessageObserver boom = e -> { throw new RuntimeException("simulated failure"); };

        assertDoesNotThrow(() ->
            MessageObserverDispatcher.dispatch(channelName, channelId,
                    message(MessageType.DONE, "finished", "corr-2"),
                    List.of(boom, captured::add)));

        assertEquals(1, captured.size());
    }

    @Test
    void dispatch_allObserversThrow_doesNotPropagateException() {
        final MessageObserver boom1 = e -> { throw new RuntimeException("boom1"); };
        final MessageObserver boom2 = e -> { throw new RuntimeException("boom2"); };

        assertDoesNotThrow(() ->
            MessageObserverDispatcher.dispatch(channelName, channelId,
                    message(MessageType.FAILURE, "error", null),
                    List.of(boom1, boom2)));
    }

    // ── Nullable fields ───────────────────────────────────────────────────

    @Test
    void dispatch_nullChannel_channelNameIsNull() {
        final List<MessageReceivedEvent> captured = new ArrayList<>();

        MessageObserverDispatcher.dispatch(null, channelId,
                message(MessageType.QUERY, "hello", null),
                List.of(captured::add));

        assertNull(captured.get(0).channelName());
    }

    @Test
    void dispatch_nullCorrelationId_propagatesNull() {
        final List<MessageReceivedEvent> captured = new ArrayList<>();

        MessageObserverDispatcher.dispatch(channelName, channelId,
                message(MessageType.STATUS, "working", null),
                List.of(captured::add));

        assertNull(captured.get(0).correlationId());
    }

    @Test
    void dispatch_noObservers_noException() {
        assertDoesNotThrow(() ->
            MessageObserverDispatcher.dispatch(channelName, channelId,
                    message(MessageType.HANDOFF, "over to you", "corr-3"),
                    List.of()));
    }

    // ── MessageReceivedEvent compact constructor ───────────────────────────

    @Test
    void messageReceivedEvent_eventWithContent_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
            new io.casehub.qhorus.api.gateway.MessageReceivedEvent(
                channelName, channelId, MessageType.EVENT, "agent-a", null, "non-null content"));
    }

    @Test
    void messageReceivedEvent_eventWithNullContent_isValid() {
        assertDoesNotThrow(() ->
            new io.casehub.qhorus.api.gateway.MessageReceivedEvent(
                channelName, channelId, MessageType.EVENT, "agent-a", null, null));
    }
}
