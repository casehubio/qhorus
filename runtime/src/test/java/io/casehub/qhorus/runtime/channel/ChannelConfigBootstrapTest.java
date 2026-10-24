package io.casehub.qhorus.runtime.channel;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.EnforcementMode;
import io.casehub.qhorus.api.channel.FindOrCreateResult;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.runtime.config.QhorusConfig.ChannelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChannelConfigBootstrapTest {

    private ChannelService channelService;
    private SpaceService spaceService;

    @BeforeEach
    void setUp() {
        channelService = mock(ChannelService.class);
        spaceService = mock(SpaceService.class);
    }

    @Test
    void emptyConfigDoesNothing() {
        var bootstrap = bootstrap(Map.of());
        bootstrap.onStartup(null);
        verifyNoInteractions(channelService);
    }

    @Test
    void singleChannelCreated() {
        var bootstrap = bootstrap(Map.of("reviews", minimalConfig()));
        when(channelService.findOrCreate(any()))
                .thenReturn(new FindOrCreateResult(stubChannel("reviews", ChannelSemantic.APPEND), true));

        bootstrap.onStartup(null);

        var captor = ArgumentCaptor.forClass(ChannelCreateRequest.class);
        verify(channelService).findOrCreate(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("reviews");
        assertThat(captor.getValue().semantic()).isEqualTo(ChannelSemantic.APPEND);
    }

    @Test
    void channelWithAllFieldsMapsCorrectly() {
        var cfg = fullConfig();
        var bootstrap = bootstrap(Map.of("full-channel", cfg));
        when(channelService.findOrCreate(any()))
                .thenReturn(new FindOrCreateResult(stubChannel("full-channel", ChannelSemantic.COLLECT), true));
        when(spaceService.findByName("ops")).thenReturn(
                Optional.of(new Space(UUID.randomUUID(), "ops", "Ops space", null, "default", Instant.now())));

        bootstrap.onStartup(null);

        var captor = ArgumentCaptor.forClass(ChannelCreateRequest.class);
        verify(channelService).findOrCreate(captor.capture());
        var req = captor.getValue();
        assertThat(req.semantic()).isEqualTo(ChannelSemantic.COLLECT);
        assertThat(req.description()).isEqualTo("Full test channel");
        assertThat(req.allowedWriters()).containsExactly("agent-a", "agent-b");
        assertThat(req.adminInstances()).containsExactly("admin-1");
        assertThat(req.barrierContributors()).containsExactly("contrib-1");
        assertThat(req.reviewerInstances()).containsExactly("reviewer-1");
        assertThat(req.allowedTypes()).containsExactlyInAnyOrder(MessageType.QUERY, MessageType.COMMAND);
        assertThat(req.deniedTypes()).containsExactly(MessageType.EVENT);
        assertThat(req.protocols()).containsExactly("REQUEST_RESPONSE");
        assertThat(req.protocolParticipants()).containsExactly("agent-a");
        assertThat(req.enforcementMode()).isEqualTo(EnforcementMode.BLOCKING);
        assertThat(req.enforcementExclusions()).containsExactly("CORRELATION_INTEGRITY");
        assertThat(req.rateLimitPerChannel()).isEqualTo(100);
        assertThat(req.rateLimitPerInstance()).isEqualTo(10);
        assertThat(req.routingTrustThreshold()).isEqualTo(0.5);
        assertThat(req.trackDelivery()).isTrue();
        assertThat(req.spaceId()).isNotNull();
        assertThat(req.metadata()).containsEntry("team", "platform");
    }

    @Test
    void spaceNotFoundSkipsSpaceAssignment() {
        var cfg = configWithSpace("nonexistent");
        var bootstrap = bootstrap(Map.of("review", cfg));
        when(spaceService.findByName("nonexistent")).thenReturn(Optional.empty());
        when(channelService.findOrCreate(any()))
                .thenReturn(new FindOrCreateResult(stubChannel("review", ChannelSemantic.APPEND), true));

        bootstrap.onStartup(null);

        var captor = ArgumentCaptor.forClass(ChannelCreateRequest.class);
        verify(channelService).findOrCreate(captor.capture());
        assertThat(captor.getValue().spaceId()).isNull();
    }

    @Test
    void ambiguousSpaceSkipsSpaceAssignment() {
        var cfg = configWithSpace("ambiguous");
        var bootstrap = bootstrap(Map.of("review", cfg));
        when(spaceService.findByName("ambiguous")).thenThrow(
                new IllegalStateException("Ambiguous space name 'ambiguous'"));
        when(channelService.findOrCreate(any()))
                .thenReturn(new FindOrCreateResult(stubChannel("review", ChannelSemantic.APPEND), true));

        bootstrap.onStartup(null);

        var captor = ArgumentCaptor.forClass(ChannelCreateRequest.class);
        verify(channelService).findOrCreate(captor.capture());
        assertThat(captor.getValue().spaceId()).isNull();
    }

    @Test
    void connectorBindingMapsAllFourFields() {
        var cfg = configWithConnector();
        var bootstrap = bootstrap(Map.of("support", cfg));
        when(channelService.findOrCreate(any()))
                .thenReturn(new FindOrCreateResult(stubChannel("support", ChannelSemantic.APPEND), true));

        bootstrap.onStartup(null);

        var captor = ArgumentCaptor.forClass(ChannelCreateRequest.class);
        verify(channelService).findOrCreate(captor.capture());
        var req = captor.getValue();
        assertThat(req.inboundConnectorId()).isEqualTo("slack");
        assertThat(req.externalKey()).isEqualTo("C0SUPPORT");
        assertThat(req.outboundConnectorId()).isEqualTo("slack");
        assertThat(req.outboundDestination()).isEqualTo("#support");
    }

    @Test
    void exceptionInOneChannelDoesNotBlockOthers() {
        var channels = new LinkedHashMap<String, ChannelConfig>();
        channels.put("channel-a", minimalConfig());
        channels.put("channel-b", minimalConfig());
        var bootstrap = bootstrap(channels);
        when(channelService.findOrCreate(any()))
                .thenAnswer(inv -> {
                    ChannelCreateRequest req = inv.getArgument(0);
                    if ("channel-a".equals(req.name())) throw new RuntimeException("boom");
                    return new FindOrCreateResult(stubChannel(req.name(), ChannelSemantic.APPEND), true);
                });

        bootstrap.onStartup(null);

        verify(channelService, times(2)).findOrCreate(any());
    }

    // --- helpers ---


    @Test
    void existingChannelWithNoDrift() {
        var cfg = new StubChannelConfig(ChannelSemantic.APPEND, "Reviews", null, null, null, null,
                                        null, null, null, null, null, null, null, null, null, null, null, null, null);
        var existing = Channel.builder("reviews").id(UUID.randomUUID()).semantic(ChannelSemantic.APPEND)
                              .description("Reviews").tenancyId("default")
                              .createdAt(Instant.now()).lastActivityAt(Instant.now()).build();
        var bootstrap = bootstrap(Map.of("reviews", cfg));
        when(channelService.findOrCreate(any()))
                .thenReturn(new FindOrCreateResult(existing, false));

        bootstrap.onStartup(null);

        var drifts = bootstrap.detectDrift("reviews", cfg, existing);
        assertThat(drifts).isEmpty();
    }

    @Test
    void existingChannelWithSemanticDrift() {
        var cfg = new StubChannelConfig(ChannelSemantic.COLLECT, null, null, null, null, null,
                                        null, null, null, null, null, null, null, null, null, null, null, null, null);
        var existing = Channel.builder("reviews").id(UUID.randomUUID()).semantic(ChannelSemantic.APPEND)
                              .tenancyId("default").createdAt(Instant.now()).lastActivityAt(Instant.now()).build();

        var bootstrap = bootstrap(Map.of("reviews", cfg));
        var drifts    = bootstrap.detectDrift("reviews", cfg, existing);
        assertThat(drifts).hasSize(1);
        assertThat(drifts.get(0).field()).isEqualTo("semantic");
        assertThat(drifts.get(0).declared()).isEqualTo("COLLECT");
        assertThat(drifts.get(0).actual()).isEqualTo("APPEND");
    }

    @Test
    void existingChannelWithMultipleDrifts() {
        var cfg = new StubChannelConfig(ChannelSemantic.BROADCAST, "Updated desc",
                                        List.of("agent-x"), null, null, null,
                                        null, null, null, null, EnforcementMode.BLOCKING, null,
                                        50, null, null, true, null, null, null);
        var existing = Channel.builder("status").id(UUID.randomUUID()).semantic(ChannelSemantic.APPEND)
                              .description("Original desc").allowedWriters(List.of("agent-y"))
                              .tenancyId("default").createdAt(Instant.now()).lastActivityAt(Instant.now()).build();

        var bootstrap = bootstrap(Map.of("status", cfg));
        var drifts    = bootstrap.detectDrift("status", cfg, existing);
        assertThat(drifts).hasSizeGreaterThanOrEqualTo(4);
        assertThat(drifts).extracting(ChannelConfigBootstrap.ChannelDrift::field)
                          .contains("semantic", "description", "allowedWriters", "enforcementMode");
    }

    @Test
    void driftNotReportedForUndeclaredFields() {
        var cfg = minimalConfig();
        var existing = Channel.builder("reviews").id(UUID.randomUUID()).semantic(ChannelSemantic.APPEND)
                              .description("Some desc").allowedWriters(List.of("agent-1"))
                              .rateLimitPerChannel(100).enforcementMode(EnforcementMode.BLOCKING)
                              .tenancyId("default").createdAt(Instant.now()).lastActivityAt(Instant.now()).build();

        var bootstrap = bootstrap(Map.of("reviews", cfg));
        var drifts    = bootstrap.detectDrift("reviews", cfg, existing);
        assertThat(drifts).isEmpty();
    }

    private ChannelConfigBootstrap bootstrap(Map<String, ChannelConfig> channels) {
        return new ChannelConfigBootstrap(configWith(channels), channelService, spaceService);
    }

    private static QhorusConfig configWith(Map<String, ChannelConfig> channels) {
        return new StubQhorusConfig(channels);
    }

    private static ChannelConfig minimalConfig() {
        return new StubChannelConfig(ChannelSemantic.APPEND, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static ChannelConfig fullConfig() {
        return new StubChannelConfig(
                ChannelSemantic.COLLECT,
                "Full test channel",
                List.of("agent-a", "agent-b"),
                List.of("admin-1"),
                List.of("contrib-1"),
                List.of("reviewer-1"),
                List.of(MessageType.QUERY, MessageType.COMMAND),
                List.of(MessageType.EVENT),
                List.of("REQUEST_RESPONSE"),
                List.of("agent-a"),
                EnforcementMode.BLOCKING,
                List.of("CORRELATION_INTEGRITY"),
                100, 10, 0.5, true, "ops",
                Map.of("team", "platform"),
                null);
    }

    private static ChannelConfig configWithSpace(String spaceName) {
        return new StubChannelConfig(ChannelSemantic.APPEND, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, spaceName, null, null);
    }

    private static ChannelConfig configWithConnector() {
        return new StubChannelConfig(ChannelSemantic.APPEND, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                new StubConnectorConfig("slack", "C0SUPPORT", "slack", "#support"));
    }

    private static Channel stubChannel(String name, ChannelSemantic semantic) {
        return Channel.builder(name).id(UUID.randomUUID()).semantic(semantic)
                .tenancyId("default").createdAt(Instant.now()).lastActivityAt(Instant.now()).build();
    }

    record StubConnectorConfig(String inboundConnectorId, String externalKey,
                               String outboundConnectorId, String outboundDestination)
            implements ChannelConfig.ConnectorConfig {}

    record StubChannelConfig(
            ChannelSemantic semantic,
            String descriptionValue,
            List<String> allowedWritersValue,
            List<String> adminInstancesValue,
            List<String> barrierContributorsValue,
            List<String> reviewerInstancesValue,
            List<MessageType> allowedTypesValue,
            List<MessageType> deniedTypesValue,
            List<String> protocolsValue,
            List<String> protocolParticipantsValue,
            EnforcementMode enforcementModeValue,
            List<String> enforcementExclusionsValue,
            Integer rateLimitPerChannelValue,
            Integer rateLimitPerInstanceValue,
            Double routingTrustThresholdValue,
            Boolean trackDeliveryValue,
            String spaceValue,
            Map<String, String> metadataValue,
            ChannelConfig.ConnectorConfig connectorValue
    ) implements ChannelConfig {
        @Override public Optional<String> description() { return Optional.ofNullable(descriptionValue); }
        @Override public Optional<List<String>> allowedWriters() { return Optional.ofNullable(allowedWritersValue); }
        @Override public Optional<List<String>> adminInstances() { return Optional.ofNullable(adminInstancesValue); }
        @Override public Optional<List<String>> barrierContributors() { return Optional.ofNullable(barrierContributorsValue); }
        @Override public Optional<List<String>> reviewerInstances() { return Optional.ofNullable(reviewerInstancesValue); }
        @Override public Optional<List<MessageType>> allowedTypes() { return Optional.ofNullable(allowedTypesValue); }
        @Override public Optional<List<MessageType>> deniedTypes() { return Optional.ofNullable(deniedTypesValue); }
        @Override public Optional<List<String>> protocols() { return Optional.ofNullable(protocolsValue); }
        @Override public Optional<List<String>> protocolParticipants() { return Optional.ofNullable(protocolParticipantsValue); }
        @Override public Optional<EnforcementMode> enforcementMode() { return Optional.ofNullable(enforcementModeValue); }
        @Override public Optional<List<String>> enforcementExclusions() { return Optional.ofNullable(enforcementExclusionsValue); }
        @Override public OptionalInt rateLimitPerChannel() { return rateLimitPerChannelValue != null ? OptionalInt.of(rateLimitPerChannelValue) : OptionalInt.empty(); }
        @Override public OptionalInt rateLimitPerInstance() { return rateLimitPerInstanceValue != null ? OptionalInt.of(rateLimitPerInstanceValue) : OptionalInt.empty(); }
        @Override public Optional<Double> routingTrustThreshold() { return Optional.ofNullable(routingTrustThresholdValue); }
        @Override public Optional<Boolean> trackDelivery() { return Optional.ofNullable(trackDeliveryValue); }
        @Override public Optional<String> space() { return Optional.ofNullable(spaceValue); }
        @Override public Map<String, String> metadata() { return metadataValue != null ? metadataValue : Map.of(); }
        @Override public Optional<ChannelConfig.ConnectorConfig> connector() { return Optional.ofNullable(connectorValue); }
        @Override public Optional<io.casehub.qhorus.api.channel.ParticipationPolicyMode> participationPolicy() { return Optional.empty(); }
    }

    static class StubQhorusConfig implements QhorusConfig {
        private final Map<String, ChannelConfig> channels;
        StubQhorusConfig(Map<String, ChannelConfig> channels) { this.channels = channels; }
        @Override public Map<String, ChannelConfig> channels() { return channels; }
        @Override public Cleanup cleanup() { return null; }
        @Override public AgentCard agentCard() { return null; }
        @Override public A2a a2a() { return null; }
        @Override public Watchdog watchdog() { return null; }
        @Override public Attestation attestation() { return null; }
        @Override public Commitment commitment() { return null; }
        @Override public Summary summary() { return null; }
        @Override public Protocol protocol() { return null; }
        @Override public Routing routing() { return null; }
        @Override public ConnectorBackend connectorBackend() { return null; }
        @Override public Correction correction() { return null; }
        @Override public Erasure erasure() { return null; }
        @Override public Instance instance() { return null; }
        @Override public Mesh mesh() { return null; }
    }
}
