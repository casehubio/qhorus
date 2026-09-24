package io.casehub.qhorus.graphql.channels;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.ChannelSummary;
import io.casehub.qhorus.api.channel.ChannelSummaryManager;
import io.casehub.qhorus.api.channel.ChannelSummaryResult;
import io.casehub.qhorus.api.channel.CapacityThresholdConfig;
import io.casehub.qhorus.api.channel.ClearChannelResult;
import io.casehub.qhorus.api.channel.MembershipManager;
import io.casehub.qhorus.api.channel.ProjectionReader;
import io.casehub.qhorus.api.channel.ProtocolReader;
import io.casehub.qhorus.api.channel.RoutingDiagnostics;
import io.casehub.qhorus.api.channel.SpaceManager;
import io.casehub.qhorus.api.channel.TopicManager;
import io.casehub.qhorus.api.channel.UnreadCountProvider;
import io.casehub.qhorus.api.gateway.BackendRegistry;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.store.MessageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelsServiceSummaryTest {

    private ChannelsService service;
    private ChannelReader channelReader;
    private ChannelSummaryManager channelSummaryManager;
    private CurrentPrincipal currentPrincipal;

    @BeforeEach
    void setUp() {
        channelReader = mock(ChannelReader.class);
        channelSummaryManager = mock(ChannelSummaryManager.class);
        currentPrincipal = mock(CurrentPrincipal.class);
        when(currentPrincipal.actorId()).thenReturn("test-actor");
        service = new ChannelsService(
                channelReader,
                mock(ConsumerMessaging.class),
                mock(ChannelManager.class),
                mock(TopicManager.class),
                mock(MembershipManager.class),
                mock(UnreadCountProvider.class),
                mock(SpaceManager.class),
                mock(BackendRegistry.class),
                mock(MessageStore.class),
                currentPrincipal,
                channelSummaryManager,
                mock(ProjectionReader.class),
                mock(ProtocolReader.class),
                mock(RoutingDiagnostics.class),
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void channelSummaryReturnsExisting() {
        UUID id = UUID.randomUUID();
        Channel ch = createChannel("test", id);
        when(channelReader.findById(id)).thenReturn(Optional.of(ch));
        ChannelSummary summary = ChannelSummary.builder(id).content("summary text")
                .annotations(Map.of("key", "val")).updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedBy("actor-1").tenancyId("t").build();
        when(channelSummaryManager.getSummary(id)).thenReturn(Optional.of(summary));

        ChannelSummaryResult result = service.channelSummary(id);

        assertThat(result.channelName()).isEqualTo("test");
        assertThat(result.content()).isEqualTo("summary text");
        assertThat(result.updatedBy()).isEqualTo("actor-1");
    }

    @Test
    void channelSummaryReturnsEmptyWhenNone() {
        UUID id = UUID.randomUUID();
        Channel ch = createChannel("test", id);
        when(channelReader.findById(id)).thenReturn(Optional.of(ch));
        when(channelSummaryManager.getSummary(id)).thenReturn(Optional.empty());

        ChannelSummaryResult result = service.channelSummary(id);

        assertThat(result.channelName()).isEqualTo("test");
        assertThat(result.content()).isNull();
    }

    @Test
    void updateChannelSummaryDelegates() {
        UUID id = UUID.randomUUID();
        Channel ch = createChannel("test", id);
        when(channelReader.findById(id)).thenReturn(Optional.of(ch));
        ChannelSummary saved = ChannelSummary.builder(id).content("new summary")
                .updatedBy("test-actor").tenancyId("t").build();
        when(channelSummaryManager.setSummary(id, "new summary", "test-actor")).thenReturn(saved);

        ChannelSummaryResult result = service.updateChannelSummary(id, "new summary");

        assertThat(result.content()).isEqualTo("new summary");
    }

    @Test
    void configureChannelSummaryDelegates() {
        UUID id = UUID.randomUUID();
        Channel ch = createChannel("test", id);
        when(channelReader.findById(id)).thenReturn(Optional.of(ch));
        ChannelSummary saved = ChannelSummary.builder(id).updateAfterMessages(5)
                .updateAfterSeconds(60).tenancyId("t").build();
        when(channelSummaryManager.configureSummary(id, 5, 60)).thenReturn(saved);

        ChannelSummaryResult result = service.configureChannelSummary(id, 5, 60);

        assertThat(result.updateAfterMessages()).isEqualTo(5);
        assertThat(result.updateAfterSeconds()).isEqualTo(60);
    }

    @Test
    void triggerChannelSummaryUpdateThrowsWhenNotConfigured() {
        UUID id = UUID.randomUUID();
        Channel ch = createChannel("test", id);
        when(channelReader.findById(id)).thenReturn(Optional.of(ch));
        when(channelSummaryManager.triggerUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.triggerChannelSummaryUpdate(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No summary configured");
    }

    @Test
    void redistributionThresholdReturnsConfig() {
        UUID id = UUID.randomUUID();
        Channel ch = Channel.builder("test").id(id).semantic(ChannelSemantic.APPEND)
                .tenancyId("t").createdAt(Instant.now()).lastActivityAt(Instant.now())
                .redistributionCapacityThreshold(0.9)
                .build();
        when(channelReader.findById(id)).thenReturn(Optional.of(ch));

        CapacityThresholdConfig result = service.redistributionThreshold(id);

        assertThat(result.configured()).isEqualTo(0.9);
        assertThat(result.effective()).isEqualTo(0.9);
    }

    @Test
    void redistributionThresholdUsesDefaultWhenNull() {
        UUID id = UUID.randomUUID();
        Channel ch = createChannel("test", id);
        when(channelReader.findById(id)).thenReturn(Optional.of(ch));

        CapacityThresholdConfig result = service.redistributionThreshold(id);

        assertThat(result.configured()).isNull();
        assertThat(result.effective()).isEqualTo(0.85);
    }

    private Channel createChannel(String name, UUID id) {
        return Channel.builder(name)
                .id(id)
                .semantic(ChannelSemantic.APPEND)
                .tenancyId("test-tenant")
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();
    }
}
