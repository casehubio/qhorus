package io.casehub.qhorus.graphql.channels;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.BackendInfo;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelEnforcementConfig;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelProtocolConfig;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.ChannelRoutingConfig;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.ChannelSummaryManager;
import io.casehub.qhorus.api.channel.EnforcementMode;
import io.casehub.qhorus.api.channel.MembershipManager;
import io.casehub.qhorus.api.channel.ProjectionReader;
import io.casehub.qhorus.api.channel.ProtocolReader;
import io.casehub.qhorus.api.channel.RoutingCandidate;
import io.casehub.qhorus.api.channel.RoutingDiagnostic;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelsServiceConfigTest {

    private ChannelsService service;
    private ChannelManager channelManager;
    private ChannelReader channelReader;
    private ProtocolReader protocolReader;
    private RoutingDiagnostics routingDiagnostics;
    private ChannelSummaryManager channelSummaryManager;
    private ProjectionReader projectionReader;

    @BeforeEach
    void setUp() {
        channelReader = mock(ChannelReader.class);
        channelManager = mock(ChannelManager.class);
        protocolReader = mock(ProtocolReader.class);
        routingDiagnostics = mock(RoutingDiagnostics.class);
        channelSummaryManager = mock(ChannelSummaryManager.class);
        projectionReader = mock(ProjectionReader.class);
        service = new ChannelsService(
                channelReader,
                mock(ConsumerMessaging.class),
                channelManager,
                mock(TopicManager.class),
                mock(MembershipManager.class),
                mock(UnreadCountProvider.class),
                mock(SpaceManager.class),
                mock(BackendRegistry.class),
                mock(MessageStore.class),
                mock(CurrentPrincipal.class),
                channelSummaryManager,
                projectionReader,
                protocolReader,
                routingDiagnostics);
    }

    @Test
    void setChannelRateLimitsDelegates() {
        UUID id = UUID.randomUUID();
        Channel ch = createChannel("test", id);
        when(channelManager.setRateLimits(id, 10, 5)).thenReturn(ch);

        Channel result = service.setChannelRateLimits(id, 10, 5);

        assertThat(result.name()).isEqualTo("test");
        verify(channelManager).setRateLimits(id, 10, 5);
    }

    @Test
    void setChannelWritersDelegates() {
        UUID id = UUID.randomUUID();
        Channel ch = createChannel("test", id);
        when(channelManager.setAllowedWriters(id, List.of("agent-1"))).thenReturn(ch);

        Channel result = service.setChannelWriters(id, List.of("agent-1"));

        assertThat(result).isNotNull();
        verify(channelManager).setAllowedWriters(id, List.of("agent-1"));
    }

    @Test
    void protocolsReturnsRegisteredNames() {
        when(protocolReader.registeredProtocols()).thenReturn(List.of("ROUND_ROBIN", "REQUEST_RESPONSE"));

        List<String> result = service.protocols();

        assertThat(result).containsExactly("ROUND_ROBIN", "REQUEST_RESPONSE");
    }

    @Test
    void channelProtocolsReturnsConfig() {
        UUID id = UUID.randomUUID();
        Channel ch = Channel.builder("test").id(id).semantic(ChannelSemantic.APPEND)
                .tenancyId("t").createdAt(Instant.now()).lastActivityAt(Instant.now())
                .protocols(List.of("ROUND_ROBIN")).protocolParticipants(List.of("a", "b"))
                .build();
        when(channelReader.findById(id)).thenReturn(Optional.of(ch));

        ChannelProtocolConfig result = service.channelProtocols(id);

        assertThat(result.protocols()).containsExactly("ROUND_ROBIN");
        assertThat(result.protocolParticipants()).containsExactly("a", "b");
    }

    @Test
    void channelEnforcementReturnsConfig() {
        UUID id = UUID.randomUUID();
        Channel ch = Channel.builder("test").id(id).semantic(ChannelSemantic.APPEND)
                .tenancyId("t").createdAt(Instant.now()).lastActivityAt(Instant.now())
                .enforcementMode(EnforcementMode.BLOCKING)
                .enforcementExclusions(List.of("TYPE_POLICY"))
                .build();
        when(channelReader.findById(id)).thenReturn(Optional.of(ch));
        when(protocolReader.registeredProtocols()).thenReturn(List.of("ROUND_ROBIN"));

        ChannelEnforcementConfig result = service.channelEnforcement(id);

        assertThat(result.enforcementMode()).isEqualTo("BLOCKING");
        assertThat(result.enforcementExclusions()).containsExactly("TYPE_POLICY");
        assertThat(result.availableSources()).contains("TYPE_POLICY", "CORRELATION_INTEGRITY", "ROUND_ROBIN");
    }

    @Test
    void setEnforcementModeValidatesInput() {
        assertThatThrownBy(() -> service.setEnforcementMode(UUID.randomUUID(), "INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid enforcement mode");
    }

    @Test
    void routingConfigReturnsThresholds() {
        UUID id = UUID.randomUUID();
        Channel ch = Channel.builder("test").id(id).semantic(ChannelSemantic.APPEND)
                .tenancyId("t").createdAt(Instant.now()).lastActivityAt(Instant.now())
                .routingTrustThreshold(0.5)
                .build();
        when(channelReader.findById(id)).thenReturn(Optional.of(ch));
        when(routingDiagnostics.effectiveThreshold(ch)).thenReturn(0.5);

        ChannelRoutingConfig result = service.routingConfig(id);

        assertThat(result.channelName()).isEqualTo("test");
        assertThat(result.trustThreshold()).isEqualTo(0.5);
    }

    @Test
    void routingCandidatesDelegates() {
        UUID id = UUID.randomUUID();
        RoutingDiagnostic diag = new RoutingDiagnostic(
                List.of(new RoutingCandidate("agent-1", "Agent 1", 0.8, true)),
                "agent-1", 0.8, "selected", null, 0.5, true);
        when(routingDiagnostics.diagnose(eq("analyst"), eq(id), any())).thenReturn(diag);

        RoutingDiagnostic result = service.routingCandidates("analyst", id);

        assertThat(result.routingAvailable()).isTrue();
        assertThat(result.candidates()).hasSize(1);
    }

    @Test
    void setRoutingConfigValidatesThreshold() {
        assertThatThrownBy(() -> service.setRoutingConfig(UUID.randomUUID(), 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void projectionsReturnsNames() {
        when(projectionReader.registeredNames()).thenReturn(List.of("channel-summary", "timeline"));

        List<String> result = service.projections();

        assertThat(result).containsExactly("channel-summary", "timeline");
    }

    @Test
    void projectChannelDelegates() {
        UUID id = UUID.randomUUID();
        when(projectionReader.project(id, "channel-summary", null, null)).thenReturn("projected output");

        String result = service.projectChannel(id, "channel-summary", null, null);

        assertThat(result).isEqualTo("projected output");
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
