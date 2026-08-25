package io.casehub.qhorus.runtime.message;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentMatch;
import io.casehub.eidos.api.AgentQuery;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.eidos.api.AgentSelection;
import io.casehub.eidos.api.AgentSelector;
import io.casehub.eidos.api.EscalationKind;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.RoutingRejectedException;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingBridgeTest {

    private AgentRegistry registry;
    private AgentSelector selector;
    private io.casehub.ledger.runtime.service.TrustGateService trustGateService;
    private QhorusConfig config;
    private QhorusConfig.Routing routingConfig;
    private RoutingBridge bridge;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        registry = Mockito.mock(AgentRegistry.class);
        selector = Mockito.mock(AgentSelector.class);
        config = Mockito.mock(QhorusConfig.class);
        routingConfig = Mockito.mock(QhorusConfig.Routing.class);
        when(config.routing()).thenReturn(routingConfig);
        when(routingConfig.defaultTrustThreshold()).thenReturn(0.0);

        jakarta.enterprise.inject.Instance<AgentRegistry> registryInstance = Mockito.mock(jakarta.enterprise.inject.Instance.class);
        when(registryInstance.isResolvable()).thenReturn(true);
        when(registryInstance.get()).thenReturn(registry);

        jakarta.enterprise.inject.Instance<AgentSelector> selectorInstance = Mockito.mock(jakarta.enterprise.inject.Instance.class);
        when(selectorInstance.isResolvable()).thenReturn(true);
        when(selectorInstance.get()).thenReturn(selector);

        trustGateService = Mockito.mock(io.casehub.ledger.runtime.service.TrustGateService.class);
        when(trustGateService.currentScore(any(String.class))).thenReturn(java.util.OptionalDouble.of(0.5));
        jakarta.enterprise.inject.Instance<io.casehub.ledger.runtime.service.TrustGateService> trustInstance = Mockito.mock(jakarta.enterprise.inject.Instance.class);
        when(trustInstance.isResolvable()).thenReturn(true);
        when(trustInstance.get()).thenReturn(trustGateService);

        bridge = new RoutingBridge(registryInstance, selectorInstance, trustInstance, config);
    }

    @Test
    void resolve_nullTarget_returnsNull() {
        var dispatch = dispatch(null);
        assertThat(bridge.resolve(dispatch, channel(), "tenant")).isNull();
        verify(registry, never()).find(any());
    }

    @Test
    void resolve_nonRoleTarget_returnsNull() {
        var dispatch = dispatch("agent-007");
        assertThat(bridge.resolve(dispatch, channel(), "tenant")).isNull();
        verify(registry, never()).find(any());
    }

    @Test
    void resolve_roleTarget_selected_returnsResolvedTarget() {
        var matches = List.of(agentMatch("analyst-1"));
        when(registry.find(any())).thenReturn(matches);
        when(selector.select(any(), any())).thenReturn(
                new AgentSelection.Selected(descriptor("analyst-1"), null, 0.9, "highest trust"));

        var outcome = bridge.resolve(dispatch("role:analyst"), channel(), "tenant");

        assertThat(outcome).isNotNull();
        assertThat(outcome.resolvedTarget()).isEqualTo("analyst-1");
        assertThat(outcome.originalTarget()).isEqualTo("role:analyst");
        assertThat(outcome.trustScore()).isEqualTo(0.9);
        assertThat(outcome.candidateCount()).isEqualTo(1);
    }

    @Test
    void resolve_noneQualified_throws() {
        when(registry.find(any())).thenReturn(List.of());
        when(selector.select(any(), any())).thenReturn(
                new AgentSelection.NoneQualified("no candidates"));

        assertThatThrownBy(() -> bridge.resolve(dispatch("role:analyst"), channel(), "tenant"))
                .isInstanceOf(RoutingRejectedException.class)
                .hasMessageContaining("No agent qualified")
                .hasMessageContaining("analyst");
    }

    @Test
    void resolve_escalated_throws() {
        when(registry.find(any())).thenReturn(List.of(agentMatch("agent-1")));
        when(selector.select(any(), any())).thenReturn(
                new AgentSelection.Escalated("analyst", EscalationKind.NO_QUALIFIED_AGENT, "all bootstrap"));

        assertThatThrownBy(() -> bridge.resolve(dispatch("role:analyst"), channel(), "tenant"))
                .isInstanceOf(RoutingRejectedException.class)
                .hasMessageContaining("escalation")
                .hasMessageContaining("NO_QUALIFIED_AGENT");
    }

    @Test
    void resolve_selectedBelowChannelThreshold_throws() {
        var matches = List.of(agentMatch("agent-low"));
        when(registry.find(any())).thenReturn(matches);
        when(selector.select(any(), any())).thenReturn(
                new AgentSelection.Selected(descriptor("agent-low"), null, 0.3, "best available"));

        var ch = Channel.builder("strict-channel")
                .id(UUID.randomUUID())
                .routingTrustThreshold(0.7)
                .build();

        assertThatThrownBy(() -> bridge.resolve(dispatch("role:analyst"), ch, "tenant"))
                .isInstanceOf(RoutingRejectedException.class)
                .hasMessageContaining("below channel threshold")
                .hasMessageContaining("0.70");
    }

    @Test
    void resolve_selectedAboveChannelThreshold_succeeds() {
        var matches = List.of(agentMatch("agent-good"));
        when(registry.find(any())).thenReturn(matches);
        when(selector.select(any(), any())).thenReturn(
                new AgentSelection.Selected(descriptor("agent-good"), null, 0.85, "highest trust"));

        var ch = Channel.builder("strict-channel")
                .id(UUID.randomUUID())
                .routingTrustThreshold(0.7)
                .build();

        var outcome = bridge.resolve(dispatch("role:analyst"), ch, "tenant");
        assertThat(outcome.resolvedTarget()).isEqualTo("agent-good");
    }

    @Test
    void resolve_channelThresholdNull_usesGlobalDefault() {
        when(routingConfig.defaultTrustThreshold()).thenReturn(0.5);
        var matches = List.of(agentMatch("agent-1"));
        when(registry.find(any())).thenReturn(matches);
        when(selector.select(any(), any())).thenReturn(
                new AgentSelection.Selected(descriptor("agent-1"), null, 0.6, "ok"));

        var outcome = bridge.resolve(dispatch("role:analyst"), channel(), "tenant");
        assertThat(outcome.resolvedTarget()).isEqualTo("agent-1");
    }

    @Test
    void resolve_passesCorrectCapabilityToQuery() {
        when(registry.find(any())).thenReturn(List.of());
        when(selector.select(any(), any())).thenReturn(
                new AgentSelection.NoneQualified("empty"));

        try {
            bridge.resolve(dispatch("role:code-reviewer"), channel(), "my-tenant");
        } catch (RoutingRejectedException ignored) {}

        var captor = org.mockito.ArgumentCaptor.forClass(AgentQuery.class);
        verify(registry).find(captor.capture());
        assertThat(captor.getValue().capabilityName()).isEqualTo("code-reviewer");
        assertThat(captor.getValue().tenancyId()).isEqualTo("my-tenant");
    }

    @Test
    void diagnose_selected_returnsCandidatesAndSelection() {
        var matches = List.of(agentMatch("agent-a"), agentMatch("agent-b"));
        when(registry.find(any())).thenReturn(matches);
        when(selector.select(any(), any())).thenReturn(
                new AgentSelection.Selected(descriptor("agent-a"), null, 0.8, "highest"));
        when(trustGateService.currentScore("agent-a")).thenReturn(java.util.OptionalDouble.of(0.8));
        when(trustGateService.currentScore("agent-b")).thenReturn(java.util.OptionalDouble.of(0.3));

        var diag = bridge.diagnose("analyst", channel(), "tenant");

        assertThat(diag.routingAvailable()).isTrue();
        assertThat(diag.candidates()).hasSize(2);
        assertThat(diag.selectedAgentId()).isEqualTo("agent-a");
        assertThat(diag.selectedTrustScore()).isEqualTo(0.8);
        assertThat(diag.selectionOutcome()).isEqualTo("selected");

        var candidateA = diag.candidates().stream().filter(c -> c.agentId().equals("agent-a")).findFirst().orElseThrow();
        assertThat(candidateA.trustScore()).isEqualTo(0.8);
        assertThat(candidateA.passesThreshold()).isTrue();
    }

    @Test
    void diagnose_noneQualified_returnsEmptySelection() {
        when(registry.find(any())).thenReturn(List.of());
        when(selector.select(any(), any())).thenReturn(
                new AgentSelection.NoneQualified("no candidates"));

        var diag = bridge.diagnose("analyst", channel(), "tenant");

        assertThat(diag.routingAvailable()).isTrue();
        assertThat(diag.candidates()).isEmpty();
        assertThat(diag.selectedAgentId()).isNull();
        assertThat(diag.selectionOutcome()).isEqualTo("none_qualified");
        assertThat(diag.reason()).isEqualTo("no candidates");
    }

    @Test
    void diagnose_belowThreshold_reportsOutcome() {
        var matches = List.of(agentMatch("agent-low"));
        when(registry.find(any())).thenReturn(matches);
        when(selector.select(any(), any())).thenReturn(
                new AgentSelection.Selected(descriptor("agent-low"), null, 0.3, "only one"));
        when(trustGateService.currentScore("agent-low")).thenReturn(java.util.OptionalDouble.of(0.3));

        var ch   = Channel.builder("strict").id(UUID.randomUUID()).routingTrustThreshold(0.7).build();
        var diag = bridge.diagnose("analyst", ch, "tenant");

        assertThat(diag.selectionOutcome()).isEqualTo("below_threshold");
        assertThat(diag.effectiveThreshold()).isEqualTo(0.7);
        assertThat(diag.candidates().get(0).passesThreshold()).isFalse();
    }

    @Test
    void diagnose_nullChannel_usesGlobalDefault() {
        when(routingConfig.defaultTrustThreshold()).thenReturn(0.5);
        when(registry.find(any())).thenReturn(List.of());
        when(selector.select(any(), any())).thenReturn(
                new AgentSelection.NoneQualified("empty"));

        var diag = bridge.diagnose("analyst", null, "tenant");

        assertThat(diag.effectiveThreshold()).isEqualTo(0.5);
    }

    @Test
    void effectiveThreshold_channelOverride() {
        var ch = Channel.builder("ch").id(UUID.randomUUID()).routingTrustThreshold(0.8).build();
        assertThat(bridge.effectiveThreshold(ch)).isEqualTo(0.8);
    }

    @Test
    void effectiveThreshold_nullChannel_usesGlobal() {
        when(routingConfig.defaultTrustThreshold()).thenReturn(0.3);
        assertThat(bridge.effectiveThreshold(null)).isEqualTo(0.3);
    }


    private static MessageDispatch dispatch(String target) {
        return MessageDispatch.builder()
                .channelId(UUID.randomUUID())
                .sender("sender-1")
                .type(MessageType.COMMAND)
                .content("do work")
                .target(target)
                .actorType(ActorType.AGENT)
                .build();
    }

    private static Channel channel() {
        return Channel.builder("test-ch").id(UUID.randomUUID()).build();
    }

    private static AgentMatch agentMatch(String agentId) {
        return new AgentMatch(descriptor(agentId), null);
    }

    private static AgentDescriptor descriptor(String agentId) {
        return AgentDescriptor.builder()
                .agentId(agentId)
                .name(agentId)
                .slot("default")
                .tenancyId("tenant")
                .build();
    }
}
