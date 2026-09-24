package io.casehub.qhorus.runtime.message;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.EnforcementMode;
import io.casehub.qhorus.api.message.EnforcementBlockedException;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.Severity;
import io.casehub.qhorus.api.spi.SuggestedAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnforcementGateTest {

    private Channel channel(EnforcementMode mode, List<String> exclusions) {
        return Channel.builder("test-ch")
                      .id(UUID.randomUUID())
                      .semantic(ChannelSemantic.APPEND)
                      .enforcementMode(mode)
                      .enforcementExclusions(exclusions)
                      .tenancyId("default")
                      .build();
    }

    private DispatchAdvisory warning(String source, String msg) {
        return new DispatchAdvisory(source, Severity.WARNING, msg, Map.of(), SuggestedAction.LOG);
    }

    private DispatchAdvisory critical(String source, String msg) {
        return new DispatchAdvisory(source, Severity.CRITICAL, msg, Map.of(), SuggestedAction.LOG);
    }

    private DispatchAdvisory advisory(String source, String msg) {
        return new DispatchAdvisory(source, Severity.ADVISORY, msg, Map.of(), SuggestedAction.LOG);
    }

    @Test
    void advisoryModeWarningDoesNotBlock() {
        Channel                ch         = channel(EnforcementMode.ADVISORY, List.of());
        List<DispatchAdvisory> violations = List.of(warning("REQUEST_RESPONSE", "violation"));
        assertThatCode(() ->
                               MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void advisoryModeCriticalUpgradesEnforcement() {
        Channel                ch         = channel(EnforcementMode.ADVISORY, List.of());
        List<DispatchAdvisory> violations = List.of(critical("TYPE_POLICY", "critical violation"));
        assertThatThrownBy(() ->
                                   MessageService.enforceIfRequired(ch, violations, MessageType.COMMAND, "agent-1", null))
                .isInstanceOf(EnforcementBlockedException.class)
                .satisfies(ex -> {
                    var ebe = (EnforcementBlockedException) ex;
                    assertThat(ebe.mode()).isEqualTo(EnforcementMode.ADVISORY);
                    assertThat(ebe.severityUpgrade()).isTrue();
                    assertThat(ebe.effectiveMode()).isEqualTo(EnforcementMode.BLOCKING);
                });
    }

    @Test
    void nullModeWarningDoesNotBlock() {
        Channel                ch         = channel(null, List.of());
        List<DispatchAdvisory> violations = List.of(warning("REQUEST_RESPONSE", "violation"));
        assertThatCode(() ->
                               MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void blockingModeThrowsOnWarning() {
        Channel                ch         = channel(EnforcementMode.BLOCKING, List.of());
        List<DispatchAdvisory> violations = List.of(warning("REQUEST_RESPONSE", "too many queries"));
        assertThatThrownBy(() ->
                                   MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .isInstanceOf(EnforcementBlockedException.class)
                .satisfies(ex -> {
                    var ebe = (EnforcementBlockedException) ex;
                    assertThat(ebe.mode()).isEqualTo(EnforcementMode.BLOCKING);
                    assertThat(ebe.severityUpgrade()).isFalse();
                    assertThat(ebe.violationSources()).containsExactly("REQUEST_RESPONSE");
                });
    }

    @Test
    void blockingModeAdvisoryDoesNotBlock() {
        Channel                ch         = channel(EnforcementMode.BLOCKING, List.of());
        List<DispatchAdvisory> violations = List.of(advisory("ROUND_ROBIN", "out of turn"));
        assertThatCode(() ->
                               MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void blockingModePassesWhenNoViolations() {
        Channel                ch         = channel(EnforcementMode.BLOCKING, List.of());
        List<DispatchAdvisory> violations = List.of();
        assertThatCode(() ->
                               MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void eventTypeExemptFromEnforcement() {
        Channel                ch         = channel(EnforcementMode.BLOCKING, List.of());
        List<DispatchAdvisory> violations = List.of(critical("TYPE_POLICY", "violation"));
        assertThatCode(() ->
                               MessageService.enforceIfRequired(ch, violations, MessageType.EVENT, "agent-1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void systemSenderExemptFromEnforcement() {
        Channel                ch         = channel(EnforcementMode.BLOCKING, List.of());
        List<DispatchAdvisory> violations = List.of(critical("TYPE_POLICY", "violation"));
        assertThatCode(() ->
                               MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "system:enforcement", null))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = MessageType.class, names = {"DONE", "FAILURE", "DECLINE", "RESPONSE"})
    void resolutionTypesExemptFromEnforcement(MessageType type) {
        Channel                ch         = channel(EnforcementMode.BLOCKING, List.of());
        List<DispatchAdvisory> violations = List.of(critical("TYPE_POLICY", "violation"));
        assertThatCode(() ->
                               MessageService.enforceIfRequired(ch, violations, type, "agent-1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void handoffNotExempt() {
        Channel                ch         = channel(EnforcementMode.BLOCKING, List.of());
        List<DispatchAdvisory> violations = List.of(warning("TYPE_POLICY", "violation"));
        assertThatThrownBy(() ->
                                   MessageService.enforceIfRequired(ch, violations, MessageType.HANDOFF, "agent-1", null))
                .isInstanceOf(EnforcementBlockedException.class);
    }

    @Test
    void exclusionsFilterOutSources() {
        Channel                ch         = channel(EnforcementMode.BLOCKING, List.of("TYPE_POLICY"));
        List<DispatchAdvisory> violations = List.of(warning("TYPE_POLICY", "excluded violation"));
        assertThatCode(() ->
                               MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void exclusionsDoNotFilterNonMatchingSources() {
        Channel                ch         = channel(EnforcementMode.BLOCKING, List.of("TYPE_POLICY"));
        List<DispatchAdvisory> violations = List.of(warning("REQUEST_RESPONSE", "not excluded"));
        assertThatThrownBy(() ->
                                   MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .isInstanceOf(EnforcementBlockedException.class);
    }

    @Test
    void mixedExcludedAndEnforceable() {
        Channel ch = channel(EnforcementMode.BLOCKING, List.of("TYPE_POLICY"));
        List<DispatchAdvisory> violations = List.of(
                warning("TYPE_POLICY", "excluded"),
                warning("REQUEST_RESPONSE", "enforceable"));
        assertThatThrownBy(() ->
                                   MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .isInstanceOf(EnforcementBlockedException.class)
                .satisfies(ex -> {
                    var ebe = (EnforcementBlockedException) ex;
                    assertThat(ebe.violationSources()).containsExactly("REQUEST_RESPONSE");
                });
    }

    @Test
    void quarantineModeThrows() {
        Channel                ch         = channel(EnforcementMode.QUARANTINE, List.of());
        List<DispatchAdvisory> violations = List.of(warning("TYPE_POLICY", "violation"));
        assertThatThrownBy(() ->
                                   MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .isInstanceOf(EnforcementBlockedException.class)
                .satisfies(ex -> assertThat(((EnforcementBlockedException) ex).mode())
                                         .isEqualTo(EnforcementMode.QUARANTINE));
    }
}
