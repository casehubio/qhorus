package io.casehub.qhorus.playbook.bridge;

import java.util.Map;

@FunctionalInterface
public interface PlaybookExecutor {

    Map<String, Object> execute(String yamlContent, Map<String, Object> vars);
}
