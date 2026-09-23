package io.casehub.qhorus.api.message;

import io.casehub.qhorus.api.channel.EnforcementMode;
import io.casehub.qhorus.api.spi.DispatchAdvisory;

import java.util.List;

public class EnforcementBlockedException extends IllegalStateException {

    private final EnforcementMode        mode;
    private final List<String>           violationSources;
    private final List<DispatchAdvisory> violations;
    private final boolean                severityUpgrade;

    public EnforcementBlockedException(EnforcementMode mode,
                                       List<String> violationSources,
                                       List<DispatchAdvisory> violations,
                                       boolean severityUpgrade) {
        super("Enforcement " + (severityUpgrade ? EnforcementMode.BLOCKING : mode).name()
              + ": " + violations.size() + " violation(s) from " + violationSources);
        this.mode             = mode;
        this.violationSources = List.copyOf(violationSources);
        this.violations       = List.copyOf(violations);
        this.severityUpgrade  = severityUpgrade;
    }

    public EnforcementMode mode()              {return mode;}

    public List<String> violationSources()     {return violationSources;}

    public List<DispatchAdvisory> violations() {return violations;}

    public boolean severityUpgrade()           {return severityUpgrade;}

    public EnforcementMode effectiveMode() {
        return severityUpgrade ? EnforcementMode.BLOCKING : mode;
    }
}
