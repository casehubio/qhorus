package io.casehub.qhorus.playbook.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;

class PlaybookChannelProtocolTest {

    private static ProtocolContext ctx() {
        return new ProtocolContext(
                UUID.randomUUID(), "test-channel", MessageType.STATUS, "agent-1",
                null, List.of(), List.of(), List.of(), Map.of());
    }

    @Test
    void evaluate_returnsAdvisories_fromExecutor() {
        PlaybookExecutor executor = (yaml, vars) -> Map.of("advisories", List.of(
                Map.of("severity", "WARNING", "message", "threshold exceeded")));

        PlaybookChannelProtocol protocol = new PlaybookChannelProtocol(
                "my-gate", "version: 1.0", executor);

        List<DispatchAdvisory> result = protocol.evaluate(ctx());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).source()).isEqualTo("my-gate");
        assertThat(result.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(result.get(0).message()).isEqualTo("threshold exceeded");
    }

    @Test
    void evaluate_returnsEmpty_whenNoAdvisories() {
        PlaybookExecutor executor = (yaml, vars) -> Map.of("advisories", List.of());

        PlaybookChannelProtocol protocol = new PlaybookChannelProtocol(
                "clean-gate", "v: 1", executor);

        assertThat(protocol.evaluate(ctx())).isEmpty();
    }

    @Test
    void evaluate_catchesExecutionFailure() {
        PlaybookExecutor executor = (yaml, vars) -> {
            throw new RuntimeException("YAML parse error");
        };

        PlaybookChannelProtocol protocol = new PlaybookChannelProtocol(
                "broken-gate", "invalid", executor);

        List<DispatchAdvisory> result = protocol.evaluate(ctx());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).source()).isEqualTo("playbook-bridge:execution");
        assertThat(result.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(result.get(0).message()).contains("broken-gate");
    }

    @Test
    void evaluate_passesContextVarsToExecutor() {
        UUID channelId = UUID.randomUUID();
        ProtocolContext testCtx = new ProtocolContext(
                channelId, "ops-channel", MessageType.COMMAND, "agent-x",
                "corr-123", List.of("a", "b"), List.of(), List.of(), Map.of());

        PlaybookExecutor executor = (yaml, vars) -> {
            assertThat(vars).containsEntry("channelId", channelId.toString());
            assertThat(vars).containsEntry("channelName", "ops-channel");
            assertThat(vars).containsEntry("incomingType", "COMMAND");
            assertThat(vars).containsEntry("sender", "agent-x");
            assertThat(vars).containsEntry("correlationId", "corr-123");
            assertThat(vars).containsEntry("participantCount", 2);
            return Map.of("advisories", List.of());
        };

        PlaybookChannelProtocol protocol = new PlaybookChannelProtocol(
                "var-gate", "v: 1", executor);
        protocol.evaluate(testCtx);
    }

    @Test
    void evaluate_passesIncomingContentToExecutor() {
        UUID channelId = UUID.randomUUID();
        ProtocolContext testCtx = new ProtocolContext(
                channelId, "review-channel", MessageType.STATUS, "architect",
                "corr-456", "I approve this design",
                List.of("architect", "tech-lead"), List.of(), List.of(), Map.of());

        PlaybookExecutor executor = (yaml, vars) -> {
            assertThat(vars).containsEntry("incomingContent", "I approve this design");
            return Map.of("advisories", List.of());
        };

        PlaybookChannelProtocol protocol = new PlaybookChannelProtocol(
                "content-gate", "v: 1", executor);
        protocol.evaluate(testCtx);
    }

    @Test
    void evaluate_omitsIncomingContentWhenNull() {
        ProtocolContext testCtx = new ProtocolContext(
                UUID.randomUUID(), "ch", MessageType.STATUS, "agent",
                null, List.of(), List.of(), List.of(), Map.of());

        PlaybookExecutor executor = (yaml, vars) -> {
            assertThat(vars).doesNotContainKey("incomingContent");
            return Map.of("advisories", List.of());
        };

        PlaybookChannelProtocol protocol = new PlaybookChannelProtocol(
                "null-gate", "v: 1", executor);
        protocol.evaluate(testCtx);
    }

    @Test
    void protocolName_returnsConfiguredName() {
        PlaybookChannelProtocol protocol = new PlaybookChannelProtocol(
                "my-protocol", "v: 1", (y, v) -> Map.of());
        assertThat(protocol.protocolName()).isEqualTo("my-protocol");
    }
}
