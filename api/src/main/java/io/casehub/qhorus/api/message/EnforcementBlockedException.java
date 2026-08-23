package io.casehub.qhorus.api.message;

import io.casehub.qhorus.api.channel.EnforcementMode;

import java.util.List;

public class EnforcementBlockedException extends IllegalStateException {

    private final EnforcementMode mode;
    private final List<String> violationSources;
    private final List<String> violations;

    public EnforcementBlockedException(EnforcementMode mode,
                                        List<String> violationSources,
                                        List<String> violations) {
        super("Enforcement " + mode.name() + ": " + violations.size()
              + " violation(s) from " + violationSources);
        this.mode = mode;
        this.violationSources = List.copyOf(violationSources);
        this.violations = List.copyOf(violations);
    }

    public EnforcementMode mode() { return mode; }
    public List<String> violationSources() { return violationSources; }
    public List<String> violations() { return violations; }
}
