package io.casehub.qhorus.runtime.message;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.EnforcementMode;
import io.casehub.qhorus.api.message.EnforcementBlockedException;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnforcementGateTest {

    private static final Set<MessageType> RESOLUTION_TYPES = Set.of(
            MessageType.DONE, MessageType.FAILURE, MessageType.DECLINE, MessageType.RESPONSE);

    private Channel channel(EnforcementMode mode, List<String> exclusions) {
        return Channel.builder("test-ch")
                .id(UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND)
                .enforcementMode(mode)
                .enforcementExclusions(exclusions)
                .tenancyId("default")
                .build();
    }

    @Test
    void advisoryModeNeverThrows() {
        Channel ch = channel(EnforcementMode.ADVISORY, List.of());
        List<TaggedAdvisory> violations = List.of(
                new TaggedAdvisory("REQUEST_RESPONSE", "violation"));
        assertThatCode(() ->
                MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void nullModeNeverThrows() {
        Channel ch = channel(null, List.of());
        List<TaggedAdvisory> violations = List.of(
                new TaggedAdvisory("REQUEST_RESPONSE", "violation"));
        assertThatCode(() ->
                MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void blockingModeThrowsOnViolation() {
        Channel ch = channel(EnforcementMode.BLOCKING, List.of());
        List<TaggedAdvisory> violations = List.of(
                new TaggedAdvisory("REQUEST_RESPONSE", "too many queries"));
        assertThatThrownBy(() ->
                MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .isInstanceOf(EnforcementBlockedException.class)
                .satisfies(ex -> {
                    var ebe = (EnforcementBlockedException) ex;
                    assertThat(ebe.mode()).isEqualTo(EnforcementMode.BLOCKING);
                    assertThat(ebe.violationSources()).containsExactly("REQUEST_RESPONSE");
                });
    }

    @Test
    void blockingModePassesWhenNoViolations() {
        Channel ch = channel(EnforcementMode.BLOCKING, List.of());
        List<TaggedAdvisory> violations = List.of();
        assertThatCode(() ->
                MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void eventTypeExemptFromEnforcement() {
        Channel ch = channel(EnforcementMode.BLOCKING, List.of());
        List<TaggedAdvisory> violations = List.of(
                new TaggedAdvisory("TYPE_POLICY", "violation"));
        assertThatCode(() ->
                MessageService.enforceIfRequired(ch, violations, MessageType.EVENT, "agent-1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void systemSenderExemptFromEnforcement() {
        Channel ch = channel(EnforcementMode.BLOCKING, List.of());
        List<TaggedAdvisory> violations = List.of(
                new TaggedAdvisory("TYPE_POLICY", "violation"));
        assertThatCode(() ->
                MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "system:enforcement", null))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = MessageType.class, names = {"DONE", "FAILURE", "DECLINE", "RESPONSE"})
    void resolutionTypesExemptFromEnforcement(MessageType type) {
        Channel ch = channel(EnforcementMode.BLOCKING, List.of());
        List<TaggedAdvisory> violations = List.of(
                new TaggedAdvisory("TYPE_POLICY", "violation"));
        assertThatCode(() ->
                MessageService.enforceIfRequired(ch, violations, type, "agent-1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void handoffNotExempt() {
        Channel ch = channel(EnforcementMode.BLOCKING, List.of());
        List<TaggedAdvisory> violations = List.of(
                new TaggedAdvisory("TYPE_POLICY", "violation"));
        assertThatThrownBy(() ->
                MessageService.enforceIfRequired(ch, violations, MessageType.HANDOFF, "agent-1", null))
                .isInstanceOf(EnforcementBlockedException.class);
    }

    @Test
    void exclusionsFilterOutSources() {
        Channel ch = channel(EnforcementMode.BLOCKING, List.of("TYPE_POLICY"));
        List<TaggedAdvisory> violations = List.of(
                new TaggedAdvisory("TYPE_POLICY", "excluded violation"));
        assertThatCode(() ->
                MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void exclusionsDoNotFilterNonMatchingSources() {
        Channel ch = channel(EnforcementMode.BLOCKING, List.of("TYPE_POLICY"));
        List<TaggedAdvisory> violations = List.of(
                new TaggedAdvisory("REQUEST_RESPONSE", "not excluded"));
        assertThatThrownBy(() ->
                MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .isInstanceOf(EnforcementBlockedException.class);
    }

    @Test
    void mixedExcludedAndEnforceable() {
        Channel ch = channel(EnforcementMode.BLOCKING, List.of("TYPE_POLICY"));
        List<TaggedAdvisory> violations = List.of(
                new TaggedAdvisory("TYPE_POLICY", "excluded"),
                new TaggedAdvisory("REQUEST_RESPONSE", "enforceable"));
        assertThatThrownBy(() ->
                MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .isInstanceOf(EnforcementBlockedException.class)
                .satisfies(ex -> {
                    var ebe = (EnforcementBlockedException) ex;
                    assertThat(ebe.violations()).containsExactly("enforceable");
                    assertThat(ebe.violationSources()).containsExactly("REQUEST_RESPONSE");
                });
    }

    @Test
    void quarantineModeThrows() {
        Channel ch = channel(EnforcementMode.QUARANTINE, List.of());
        List<TaggedAdvisory> violations = List.of(
                new TaggedAdvisory("TYPE_POLICY", "violation"));
        assertThatThrownBy(() ->
                MessageService.enforceIfRequired(ch, violations, MessageType.QUERY, "agent-1", null))
                .isInstanceOf(EnforcementBlockedException.class)
                .satisfies(ex -> assertThat(((EnforcementBlockedException) ex).mode())
                        .isEqualTo(EnforcementMode.QUARANTINE));
    }
}
