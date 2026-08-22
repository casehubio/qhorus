package io.casehub.qhorus.runtime.watchdog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;
import io.casehub.qhorus.api.watchdog.*;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.instance.InstanceService;
import io.casehub.qhorus.runtime.message.CommitmentService;
import io.casehub.qhorus.runtime.message.MessageService;
import jakarta.enterprise.event.Event;

class WatchdogContainmentTest {

    private WatchdogEvaluationService service;
    private ChannelService channelService;
    private InstanceService instanceService;
    private CommitmentService commitmentService;
    private MessageService messageService;
    private CrossTenantChannelStore crossTenantChannelStore;
    private Event<WatchdogAlertEvent> alertEvents;

    private static final String TID = "DEFAULT";
    private static final UUID CHANNEL_ID = UUID.randomUUID();
    private static final UUID NOTIF_CHANNEL_ID = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        service = new WatchdogEvaluationService();
        channelService = mock(ChannelService.class);
        instanceService = mock(InstanceService.class);
        commitmentService = mock(CommitmentService.class);
        messageService = mock(MessageService.class);
        crossTenantChannelStore = mock(CrossTenantChannelStore.class);
        alertEvents = mock(Event.class);

        service.channelService = channelService;
        service.instanceService = instanceService;
        service.commitmentService = commitmentService;
        service.messageService = messageService;
        service.crossTenantChannelStore = crossTenantChannelStore;
        service.alertEvents = alertEvents;
        service.objectMapper = new ObjectMapper();

        Channel notifChannel = Channel.builder("notifications").id(NOTIF_CHANNEL_ID).build();
        when(crossTenantChannelStore.findByNameAndTenancy("notifications", TID))
                .thenReturn(Optional.of(notifChannel));
    }

    private Watchdog watchdog(WatchdogConditionType type, WatchdogAction action) {
        return Watchdog.builder(type, "test-channel")
                .id(UUID.randomUUID())
                .notificationChannel("notifications")
                .tenancyId(TID)
                .action(action)
                .build();
    }

    @Test
    void alert_action_doesNotPauseOrDeregister() {
        Watchdog w = watchdog(WatchdogConditionType.LOOP_DETECTED, WatchdogAction.ALERT);
        LoopDetectedContext ctx = new LoopDetectedContext(CHANNEL_ID, "ch", "agent-a", 5, 0.95);

        service.fireAlert(w, "loop detected", ctx, Instant.now(), CHANNEL_ID);

        verify(channelService, never()).pause(any());
        verify(instanceService, never()).markOffline(anyString());
        verify(commitmentService, never()).expireByChannel(any());
    }

    @Test
    void pauseChannel_pausesAndExpiresCommitments() {
        Watchdog w = watchdog(WatchdogConditionType.LOOP_DETECTED, WatchdogAction.PAUSE_CHANNEL);
        LoopDetectedContext ctx = new LoopDetectedContext(CHANNEL_ID, "ch", "agent-a", 5, 0.95);
        when(channelService.pause(CHANNEL_ID)).thenReturn(Channel.builder("ch").id(CHANNEL_ID).build());

        service.fireAlert(w, "loop detected", ctx, Instant.now(), CHANNEL_ID);

        verify(channelService).pause(CHANNEL_ID);
        verify(commitmentService).expireByChannel(CHANNEL_ID);
        verify(instanceService, never()).markOffline(anyString());
    }

    @Test
    void deregisterAgent_marksInstanceOffline() {
        Watchdog w = watchdog(WatchdogConditionType.LOOP_DETECTED, WatchdogAction.DEREGISTER_AGENT);
        LoopDetectedContext ctx = new LoopDetectedContext(CHANNEL_ID, "ch", "agent-a", 5, 0.95);

        service.fireAlert(w, "loop detected", ctx, Instant.now(), CHANNEL_ID);

        verify(instanceService).markOffline("agent-a");
        verify(channelService, never()).pause(any());
    }

    @Test
    void deregisterAgent_noAgentIds_skips() {
        Watchdog w = watchdog(WatchdogConditionType.CHANNEL_IDLE, WatchdogAction.DEREGISTER_AGENT);
        ChannelIdleContext ctx = new ChannelIdleContext(List.of("ch"), 300L);

        service.fireAlert(w, "channel idle", ctx, Instant.now(), CHANNEL_ID);

        verify(instanceService, never()).markOffline(anyString());
    }

    @Test
    void quarantine_pausesAndDeregisters() {
        Watchdog w = watchdog(WatchdogConditionType.LOOP_DETECTED, WatchdogAction.QUARANTINE);
        LoopDetectedContext ctx = new LoopDetectedContext(CHANNEL_ID, "ch", "agent-a", 5, 0.95);
        when(channelService.pause(CHANNEL_ID)).thenReturn(Channel.builder("ch").id(CHANNEL_ID).build());

        service.fireAlert(w, "loop detected", ctx, Instant.now(), CHANNEL_ID);

        verify(channelService).pause(CHANNEL_ID);
        verify(commitmentService).expireByChannel(CHANNEL_ID);
        verify(instanceService).markOffline("agent-a");
    }

    @Test
    void nullChannelId_skipsPause() {
        Watchdog w = watchdog(WatchdogConditionType.AGENT_STALE, WatchdogAction.QUARANTINE);
        AgentStaleContext ctx = new AgentStaleContext(1, List.of("agent-stale"));

        service.fireAlert(w, "agent stale", ctx, Instant.now(), null);

        verify(channelService, never()).pause(any());
        verify(instanceService).markOffline("agent-stale");
    }

    @Test
    void notificationChannelExcluded() {
        Watchdog w = watchdog(WatchdogConditionType.LOOP_DETECTED, WatchdogAction.PAUSE_CHANNEL);
        LoopDetectedContext ctx = new LoopDetectedContext(NOTIF_CHANNEL_ID, "notifications", "agent-a", 5, 0.95);

        service.fireAlert(w, "loop detected", ctx, Instant.now(), NOTIF_CHANNEL_ID);

        verify(channelService, never()).pause(any());
    }

    @Test
    void containmentFailure_doesNotPropagate() {
        Watchdog w = watchdog(WatchdogConditionType.LOOP_DETECTED, WatchdogAction.PAUSE_CHANNEL);
        LoopDetectedContext ctx = new LoopDetectedContext(CHANNEL_ID, "ch", "agent-a", 5, 0.95);
        when(channelService.pause(CHANNEL_ID)).thenThrow(new RuntimeException("channel deleted"));

        service.fireAlert(w, "loop detected", ctx, Instant.now(), CHANNEL_ID);

        verify(alertEvents).fireAsync(any());
    }

    @Test
    void quarantine_dispatchesContainmentEvent() {
        Watchdog w = watchdog(WatchdogConditionType.LOOP_DETECTED, WatchdogAction.QUARANTINE);
        LoopDetectedContext ctx = new LoopDetectedContext(CHANNEL_ID, "ch", "agent-a", 5, 0.95);
        when(channelService.pause(CHANNEL_ID)).thenReturn(Channel.builder("ch").id(CHANNEL_ID).build());

        service.fireAlert(w, "loop detected", ctx, Instant.now(), CHANNEL_ID);

        verify(messageService, atLeast(2)).dispatch(any(MessageDispatch.class));
    }
}
