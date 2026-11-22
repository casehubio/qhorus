package io.casehub.qhorus.playbook.bridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.inject.Instance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.yaml.step.runner.PlaybookRunConfig;
import io.casehub.yaml.step.runner.PlaybookRunResult;
import io.casehub.yaml.step.runner.PlaybookRunnerCore;

class PlaybookRunnerExecutorTest {

    private PlaybookRunnerExecutor executor;
    private PlaybookRunnerCore runner;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        runner = mock(PlaybookRunnerCore.class);
        Instance<PlaybookRunnerCore> instance = mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(false);
        when(instance.get()).thenReturn(runner);
        executor = new PlaybookRunnerExecutor();
        executor.runnerInstance = instance;
    }

    @Test
    void execute_returnsOutput_onSuccess() {
        Map<String, Object> output = Map.of("advisories", List.of());
        PlaybookRunResult result = new PlaybookRunResult(
                true, "test-playbook", null, 50L, null, List.of(), output, null);
        when(runner.run(eq("version: 1.0"), any(PlaybookRunConfig.class)))
                .thenReturn(result);

        Map<String, Object> actual = executor.execute("version: 1.0", Map.of("key", "val"));

        assertThat(actual).containsKey("advisories");
    }

    @Test
    void execute_passesVarsToConfig() {
        Map<String, Object> vars = Map.of("channelId", "abc", "sender", "agent-1");
        PlaybookRunResult result = new PlaybookRunResult(
                true, "test", null, 10L, null, List.of(), Map.of(), null);
        when(runner.run(any(String.class), any(PlaybookRunConfig.class)))
                .thenAnswer(inv -> {
                    PlaybookRunConfig config = inv.getArgument(1);
                    assertThat(config.vars()).containsEntry("channelId", "abc");
                    assertThat(config.vars()).containsEntry("sender", "agent-1");
                    assertThat(config.dryRun()).isFalse();
                    return result;
                });

        executor.execute("v: 1", vars);
    }

    @Test
    void execute_throwsOnFailure() {
        PlaybookRunResult result = new PlaybookRunResult(
                false, "test", null, 10L, null, List.of(), Map.of(), "YAML parse error");
        when(runner.run(any(String.class), any(PlaybookRunConfig.class)))
                .thenReturn(result);

        assertThatThrownBy(() -> executor.execute("bad yaml", Map.of()))
                .isInstanceOf(PlaybookExecutionException.class)
                .hasMessageContaining("YAML parse error");
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_throwsWhenRunnerUnsatisfied() {
        Instance<PlaybookRunnerCore> unsatisfied = mock(Instance.class);
        when(unsatisfied.isUnsatisfied()).thenReturn(true);
        PlaybookRunnerExecutor noRunner = new PlaybookRunnerExecutor();
        noRunner.runnerInstance = unsatisfied;

        assertThatThrownBy(() -> noRunner.execute("v: 1", Map.of()))
                .isInstanceOf(PlaybookExecutionException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void execute_returnsEmptyMap_whenOutputNull() {
        PlaybookRunResult result = new PlaybookRunResult(
                true, "test", null, 10L, null, List.of(), null, null);
        when(runner.run(any(String.class), any(PlaybookRunConfig.class)))
                .thenReturn(result);

        Map<String, Object> actual = executor.execute("v: 1", Map.of());

        assertThat(actual).isEmpty();
    }
}
