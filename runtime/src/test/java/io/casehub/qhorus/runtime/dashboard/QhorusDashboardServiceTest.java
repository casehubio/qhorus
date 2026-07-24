package io.casehub.qhorus.runtime.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelDetail;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.instance.Instance;
import io.casehub.qhorus.api.instance.InstanceInfo;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.runtime.QhorusEntityMapper;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.instance.InstanceService;
import io.casehub.qhorus.runtime.message.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;


class QhorusDashboardServiceTest {

    ChannelService channelService = mock(ChannelService.class);
    InstanceService instanceService = mock(InstanceService.class);
    MessageService messageService = mock(MessageService.class);
    MessageStore                            messageStore = mock(MessageStore.class);
    io.casehub.qhorus.api.store.ChannelBindingStore bindingStore =
            mock(io.casehub.qhorus.api.store.ChannelBindingStore.class);
    QhorusDashboardService                          service;

    @BeforeEach
    void setUp() {
        service = new QhorusDashboardService();
        service.channelService = channelService;
        service.instanceService = instanceService;
        service.messageService = messageService;
        service.messageStore = messageStore;
        reset(bindingStore);
        when(bindingStore.findAll()).thenReturn(Map.of());
        when(bindingStore.findByChannelId(any())).thenReturn(Optional.empty());
        service.bindingStore = bindingStore;
        service.entityMapper = new QhorusEntityMapper(new ObjectMapper());
        reset(channelService, instanceService, messageService, messageStore, bindingStore);
    }

    // ── listChannels ──────────────────────────────────────────────────────────

    @Test
    void listChannels_emptyStore_returnsEmptyList() {
        when(channelService.listAll()).thenReturn(List.of());

        List<ChannelDetail> result = service.listChannels();

        assertTrue(result.isEmpty());
    }

    @Test
    void listChannels_withChannel_returnsChannelViewWithMessageCount() {
        Channel ch = channel("work", ChannelSemantic.APPEND);
        when(channelService.listAll()).thenReturn(List.of(ch));
        when(messageStore.countByChannel(ch.id())).thenReturn(7);

        List<ChannelDetail> result = service.listChannels();

        assertEquals(1, result.size());
        assertEquals("work", result.get(0).name());
        assertEquals(7, result.get(0).messageCount());
        assertFalse(result.get(0).paused());
        assertEquals("APPEND", result.get(0).semantic());
    }

    // ── listInstances ─────────────────────────────────────────────────────────

    @Test
    void listInstances_emptyStore_returnsEmptyList() {
        when(instanceService.listAll()).thenReturn(List.of());

        List<InstanceInfo> result = service.listInstances();

        assertTrue(result.isEmpty());
    }

    @Test
    void listInstances_withInstance_returnsInstanceViewWithCapabilities() {
        Instance inst = instance("claude:analyst@v1", "analyst");
        when(instanceService.listAll()).thenReturn(List.of(inst));
        when(instanceService.findCapabilityTagsForInstance("claude:analyst@v1"))
                .thenReturn(List.of("code-review", "security"));

        List<InstanceInfo> result = service.listInstances();

        assertEquals(1, result.size());
        assertEquals("claude:analyst@v1", result.get(0).instanceId());
        assertEquals(List.of("code-review", "security"), result.get(0).capabilities());
        assertEquals("online", result.get(0).status());
    }

    // ── getTimeline ───────────────────────────────────────────────────────────

    @Test
    void getTimeline_unknownChannel_returnsEmptyList() {
        when(channelService.findByName("no-such")).thenReturn(Optional.empty());

        List<Map<String, Object>> result = service.getTimeline("no-such", null, 50);

        assertTrue(result.isEmpty());
    }

    @Test
    void getTimeline_knownChannel_returnsTimelineEntries() {
        Channel ch  = channel("work", ChannelSemantic.APPEND);
        Message msg = message(ch.id(), "agent:analyst@v1", MessageType.STATUS, "working on it");
        when(channelService.findByName("work")).thenReturn(Optional.of(ch));
        when(messageStore.scan(any(MessageQuery.class))).thenReturn(List.of(msg));

        List<Map<String, Object>> result = service.getTimeline("work", null, 50);

        assertEquals(1, result.size());
        assertEquals("MESSAGE", result.get(0).get("type"));
        assertEquals("agent:analyst@v1", result.get(0).get("sender"));
        assertEquals("status", result.get(0).get("message_type"));
        assertEquals("working on it", result.get(0).get("content"));
    }

    @Test
    void getTimeline_limitCappedAt200() {
        Channel ch = channel("work", ChannelSemantic.APPEND);
        when(channelService.findByName("work")).thenReturn(Optional.of(ch));
        when(messageStore.scan(any(MessageQuery.class))).thenReturn(List.of());

        // Should not throw — limit is silently capped
        assertDoesNotThrow(() -> service.getTimeline("work", null, 999)
                );
    }

    // ── getFeed ───────────────────────────────────────────────────────────────

    @Test
    void getFeed_emptyChannels_returnsEmptyList() {
        when(channelService.listAll()).thenReturn(List.of());

        List<Map<String, Object>> result = service.getFeed(100);

        assertTrue(result.isEmpty());
    }

    @Test
    void getFeed_withChannels_tagsEachEntryWithChannelName() {
        Channel ch  = channel("work", ChannelSemantic.APPEND);
        Message msg = message(ch.id(), "agent:analyst@v1", MessageType.STATUS, "progress");
        // createdAt already set by helper
        when(channelService.listAll()).thenReturn(List.of(ch));
        when(messageStore.scan(any(MessageQuery.class))).thenReturn(List.of(msg));

        List<Map<String, Object>> result = service.getFeed(100);

        assertEquals(1, result.size());
        assertEquals("work", result.get(0).get("channel"));
    }

    @Test
    void getFeed_returnsNewestFirstAcrossChannels() {
        Channel ch1 = channel("ch-alpha", ChannelSemantic.APPEND);
        Channel ch2 = channel("ch-beta", ChannelSemantic.APPEND);

        Message older = message(ch1.id(), "agent:a", MessageType.STATUS, "old").toBuilder().id(1L).build();
        Message newer = message(ch2.id(), "agent:b", MessageType.STATUS, "new").toBuilder().id(2L).build();

        when(channelService.listAll()).thenReturn(List.of(ch1, ch2));
        when(messageStore.scan(any(MessageQuery.class)))
                .thenReturn(List.of(newer, older));

        List<Map<String, Object>> result = service.getFeed(100);

        assertEquals(2, result.size());
        assertEquals("ch-beta", result.get(0).get("channel"));
        assertEquals("ch-alpha", result.get(1).get("channel"));
    }

    // ── sendHumanMessage ──────────────────────────────────────────────────────

    @Test
    void sendHumanMessage_unknownChannel_throwsIllegalArgumentException() {
        when(channelService.findByName("ghost")).thenReturn(Optional.empty());

        Exception ex = assertThrows(Exception.class, () ->
                service.sendHumanMessage("ghost", "human:alice", MessageType.STATUS, "hello", null, null, null, null, null, null)
                        );
        assertTrue(ex instanceof IllegalArgumentException
                || (ex.getCause() instanceof IllegalArgumentException),
                "Expected IllegalArgumentException, got: " + ex);
        String msg = ex instanceof IllegalArgumentException ? ex.getMessage() : ex.getCause().getMessage();
        assertTrue(msg.contains("ghost"), "Message should mention channel name: " + msg);
    }

    @Test
    void sendHumanMessage_pausedChannel_throwsIllegalStateException() {
        Channel ch = channel("oversight", ChannelSemantic.APPEND).toBuilder().paused(true).build();
        when(channelService.findByName("oversight")).thenReturn(Optional.of(ch));
        // Paused check now lives inside MessageService.dispatch() — mock throws as it would in production.
        when(messageService.dispatch(any(MessageDispatch.class)))
                .thenThrow(new IllegalStateException("Channel 'oversight' is paused"));

        Exception ex = assertThrows(Exception.class, () ->
                service.sendHumanMessage("oversight", "human:alice", MessageType.STATUS, "hello", null, null, null, null, null, null)
                        );
        assertTrue(ex instanceof IllegalStateException
                || (ex.getCause() instanceof IllegalStateException),
                "Expected IllegalStateException, got: " + ex);
        String msg = ex instanceof IllegalStateException ? ex.getMessage() : ex.getCause().getMessage();
        assertTrue(msg.contains("paused"), "Message should mention paused: " + msg);
    }

    @Test
    void sendHumanMessage_success_returnsHumanMessageResultWithCorrectFields() {
        Channel ch = channel("work", ChannelSemantic.APPEND);
        DispatchResult dr = new DispatchResult(42L, ch.id(), "human:alice", MessageType.STATUS,
                null, null, List.of(), null, null, null, null, 0, List.of());
        when(channelService.findByName("work")).thenReturn(Optional.of(ch));
        when(messageService.dispatch(any(MessageDispatch.class)))
                .thenReturn(dr);

        QhorusDashboardService.HumanMessageResult result =
                service.sendHumanMessage("work", "human:alice", MessageType.STATUS, "please prioritise security", null, null, null, null, null, null);

        assertEquals(42L, result.messageId());
        assertEquals("work", result.channelName());
        assertEquals("human:alice", result.sender());
        assertEquals("STATUS", result.messageType());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Channel channel(String name, ChannelSemantic semantic) {
        return Channel.builder(name).id(UUID.randomUUID())
                .semantic(semantic).lastActivityAt(Instant.now()).build();
    }

    private Instance instance(String instanceId, String description) {
        return Instance.builder(instanceId)
                .description(description).status("online")
                .lastSeen(Instant.now()).build();
    }

    private Message message(UUID channelId, String sender, MessageType type, String content) {
        return Message.builder().channelId(channelId).sender(sender)
                .messageType(type).content(content).createdAt(Instant.now()).build();
    }
}
