package io.casehub.qhorus.playbook.bridge;

import java.util.Map;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.casehub.yaml.step.runner.PlaybookRunConfig;
import io.casehub.yaml.step.runner.PlaybookRunResult;
import io.casehub.yaml.step.runner.PlaybookRunnerCore;

@DefaultBean
@ApplicationScoped
public class PlaybookRunnerExecutor implements PlaybookExecutor {

    @Inject
    Instance<PlaybookRunnerCore> runnerInstance;

    @Override
    public Map<String, Object> execute(String yamlContent, Map<String, Object> vars) {
        if (runnerInstance.isUnsatisfied()) {
            throw new PlaybookExecutionException(
                    "PlaybookRunnerCore is not available — the consuming application "
                    + "must provide a CDI producer for PlaybookRunnerCore");
        }
        PlaybookRunnerCore runner = runnerInstance.get();
        PlaybookRunConfig config = new PlaybookRunConfig(false, vars, null, null);
        PlaybookRunResult result = runner.run(yamlContent, config);
        if (!result.success()) {
            throw new PlaybookExecutionException(
                    "Playbook execution failed: " + result.error());
        }
        return result.output() != null ? result.output() : Map.of();
    }
}
