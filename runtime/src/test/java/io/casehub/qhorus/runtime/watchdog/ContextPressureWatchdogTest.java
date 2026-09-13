package io.casehub.qhorus.runtime.watchdog;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;
import io.casehub.qhorus.api.store.CrossTenantWatchdogStore;
import io.casehub.qhorus.api.store.WatchdogStore;
import io.casehub.qhorus.api.watchdog.Watchdog;
import io.casehub.qhorus.api.watchdog.WatchdogConditionType;
import io.casehub.qhorus.api.watchdog.WatchdogAlertEvent;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextPressureWatchdogTest {

    private WatchdogEvaluationService service;
    private CrossTenantChannelStore channelStore;
    private CrossTenantWatchdogStore watchdogStore;
    private WatchdogEvaluationService.ContextPressureQuery contextPressureQuery;
    private Consumer<WatchdogAlertEvent> alertConsumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        channelStore = mock(CrossTenantChannelStore.class);
        watchdogStore = mock(CrossTenantWatchdogStore.class);
        contextPressureQuery = mock(WatchdogEvaluationService.ContextPressureQuery.class);
        alertConsumer = mock(Consumer.class);

        QhorusConfig config = mock(QhorusConfig.class);
        QhorusConfig.Watchdog watchdogConfig = mock(QhorusConfig.Watchdog.class);
        when(config.watchdog()).thenReturn(watchdogConfig);
        when(watchdogConfig.enabled()).thenReturn(true);

        service = new WatchdogEvaluationService();
        service.config = config;
        service.crossTenantChannelStore = channelStore;
        service.crossTenantWatchdogStore = watchdogStore;
        service.contextPressureQuery = contextPressureQuery;
        service.alertConsumer = alertConsumer;
        service.watchdogStore = mock(WatchdogStore.class);
    }

    @Test
    void contextPressure_aboveThreshold_firesAlert() {
        UUID channelId = UUID.randomUUID();
        Channel ch = Channel.builder("test-channel").id(channelId)
                .semantic(ChannelSemantic.APPEND).tenancyId("default").build();
        when(channelStore.listAll()).thenReturn(List.of(ch));

        Watchdog wd = Watchdog.builder(WatchdogConditionType.CONTEXT_PRESSURE, "test-channel")
                .id(UUID.randomUUID()).thresholdCount(80)
                .notificationChannel("alerts").tenancyId("default").build();
        when(watchdogStore.listAll()).thenReturn(List.of(wd));

        when(contextPressureQuery.find(channelId, "default"))
                .thenReturn(List.of(new WatchdogEvaluationService.ContextPressureEntry("agent-a", 90)));

        service.evaluateAll();

        verify(alertConsumer).accept(argThat(event ->
                event.summary().contains("CONTEXT_PRESSURE")
                && event.summary().contains("agent-a")
                && event.summary().contains("90")));
    }

    @Test
    void contextPressure_belowThreshold_noAlert() {
        UUID channelId = UUID.randomUUID();
        Channel ch = Channel.builder("test-channel").id(channelId)
                .semantic(ChannelSemantic.APPEND).tenancyId("default").build();
        when(channelStore.listAll()).thenReturn(List.of(ch));

        Watchdog wd = Watchdog.builder(WatchdogConditionType.CONTEXT_PRESSURE, "test-channel")
                .id(UUID.randomUUID()).thresholdCount(80)
                .notificationChannel("alerts").tenancyId("default").build();
        when(watchdogStore.listAll()).thenReturn(List.of(wd));

        when(contextPressureQuery.find(channelId, "default"))
                .thenReturn(List.of(new WatchdogEvaluationService.ContextPressureEntry("agent-a", 50)));

        service.evaluateAll();

        verify(alertConsumer, never()).accept(any());
    }

    @Test
    void contextPressure_noEntriesWithPct_noAlert() {
        UUID channelId = UUID.randomUUID();
        Channel ch = Channel.builder("test-channel").id(channelId)
                .semantic(ChannelSemantic.APPEND).tenancyId("default").build();
        when(channelStore.listAll()).thenReturn(List.of(ch));

        Watchdog wd = Watchdog.builder(WatchdogConditionType.CONTEXT_PRESSURE, "test-channel")
                .id(UUID.randomUUID()).thresholdCount(80)
                .notificationChannel("alerts").tenancyId("default").build();
        when(watchdogStore.listAll()).thenReturn(List.of(wd));

        when(contextPressureQuery.find(channelId, "default"))
                .thenReturn(List.of());

        service.evaluateAll();

        verify(alertConsumer, never()).accept(any());
    }

    @Test
    void contextPressure_wildcardTarget_allChannels() {
        UUID ch1Id = UUID.randomUUID();
        UUID ch2Id = UUID.randomUUID();
        Channel ch1 = Channel.builder("channel-a").id(ch1Id)
                .semantic(ChannelSemantic.APPEND).tenancyId("default").build();
        Channel ch2 = Channel.builder("channel-b").id(ch2Id)
                .semantic(ChannelSemantic.APPEND).tenancyId("default").build();
        when(channelStore.listAll()).thenReturn(List.of(ch1, ch2));

        Watchdog wd = Watchdog.builder(WatchdogConditionType.CONTEXT_PRESSURE, "*")
                .id(UUID.randomUUID()).thresholdCount(80)
                .notificationChannel("alerts").tenancyId("default").build();
        when(watchdogStore.listAll()).thenReturn(List.of(wd));

        when(contextPressureQuery.find(ch1Id, "default"))
                .thenReturn(List.of(new WatchdogEvaluationService.ContextPressureEntry("agent-x", 95)));
        when(contextPressureQuery.find(ch2Id, "default"))
                .thenReturn(List.of());

        service.evaluateAll();

        verify(alertConsumer).accept(argThat(event ->
                event.summary().contains("agent-x")
                && event.summary().contains("channel-a")));
    }
}
